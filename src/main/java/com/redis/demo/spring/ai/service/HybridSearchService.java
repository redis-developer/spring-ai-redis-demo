package com.redis.demo.spring.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 混合检索服务
 * 结合 BM25 全文检索（Lucene）和语义检索（Milvus 向量）
 * 使用 RRF (Reciprocal Rank Fusion) 算法融合检索结果
 *
 * @author Spring AI RAG Demo
 * @since 2024-12
 */
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);

    private final VectorStore vectorStore;
    private final BM25SearchService bm25SearchService;

    @Value("${hybrid.search.rrf.k:60}")
    private int rrfK;

    @Value("${hybrid.search.dense.weight:0.7}")
    private double denseWeight;

    @Value("${hybrid.search.sparse.weight:0.3}")
    private double sparseWeight;

    /**
     * 构造函数
     *
     * @param vectorStore       向量存储（Milvus）
     * @param bm25SearchService BM25 搜索服务（Lucene）
     */
    public HybridSearchService(VectorStore vectorStore, BM25SearchService bm25SearchService) {
        this.vectorStore = vectorStore;
        this.bm25SearchService = bm25SearchService;
    }

    /**
     * 执行混合检索
     * 并行执行向量搜索和 BM25 搜索，使用 RRF 融合结果
     *
     * @param query 查询文本
     * @param topK  返回的文档数量
     * @return 融合后的文档列表
     */
    public List<Document> hybridSearch(String query, int topK) {
        logger.info("Executing hybrid search for query: '{}', topK: {}",
                query.substring(0, Math.min(50, query.length())), topK);

        // 召回更多候选用于融合
        int retrieveK = Math.max(topK * 2, 20);

        // 并行执行两种搜索
        CompletableFuture<List<RankedDoc>> vectorFuture = CompletableFuture
                .supplyAsync(() -> searchVector(query, retrieveK));

        CompletableFuture<List<RankedDoc>> bm25Future = CompletableFuture
                .supplyAsync(() -> searchBM25(query, retrieveK));

        // 等待两个搜索完成
        List<RankedDoc> vectorResults;
        List<RankedDoc> bm25Results;
        try {
            vectorResults = vectorFuture.get();
            bm25Results = bm25Future.get();
        } catch (Exception e) {
            logger.error("Hybrid search failed: {}", e.getMessage(), e);
            // 降级到纯向量搜索
            return searchVectorOnly(query, topK);
        }

        // RRF 融合
        List<Document> fusedResults = rrfFusion(vectorResults, bm25Results, topK);

        logger.info("Hybrid search completed. Vector: {}, BM25: {}, Fused: {}",
                vectorResults.size(), bm25Results.size(), fusedResults.size());

        return fusedResults;
    }

    /**
     * 向量搜索（语义检索）
     */
    private List<RankedDoc> searchVector(String query, int topK) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .build();

            List<Document> docs = vectorStore.similaritySearch(request);
            List<RankedDoc> rankedDocs = new ArrayList<>();

            for (int i = 0; i < docs.size(); i++) {
                Document doc = docs.get(i);
                String docId = doc.getId() != null ? doc.getId() : String.valueOf(i);
                // 向量搜索返回的文档按相似度排序，位置即为排名
                rankedDocs.add(new RankedDoc(docId, doc, 1.0f - (i * 0.01f), i + 1));
            }

            logger.debug("Vector search returned {} results", rankedDocs.size());
            return rankedDocs;

        } catch (Exception e) {
            logger.error("Vector search failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * BM25 搜索（关键词检索）
     */
    private List<RankedDoc> searchBM25(String query, int topK) {
        try {
            if (!bm25SearchService.isIndexBuilt()) {
                logger.warn("BM25 index not built, skipping BM25 search");
                return Collections.emptyList();
            }

            List<BM25SearchService.ScoredDocument> results = bm25SearchService.search(query, topK);
            List<RankedDoc> rankedDocs = new ArrayList<>();

            for (BM25SearchService.ScoredDocument scored : results) {
                String docId = scored.getDocument().getId() != null ? scored.getDocument().getId()
                        : UUID.randomUUID().toString();
                rankedDocs.add(new RankedDoc(docId, scored.getDocument(), scored.getScore(), scored.getRank()));
            }

            logger.debug("BM25 search returned {} results", rankedDocs.size());
            return rankedDocs;

        } catch (Exception e) {
            logger.error("BM25 search failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * RRF (Reciprocal Rank Fusion) 算法融合
     * 公式：RRF_score = Σ (weight / (k + rank))
     */
    private List<Document> rrfFusion(List<RankedDoc> vectorResults, List<RankedDoc> bm25Results, int topK) {
        Map<String, FusionScore> fusionScores = new HashMap<>();

        // 处理向量检索结果
        for (RankedDoc rankedDoc : vectorResults) {
            double rrfScore = denseWeight / (rrfK + rankedDoc.rank);
            fusionScores.computeIfAbsent(rankedDoc.id, k -> new FusionScore(rankedDoc.document))
                    .addScore(rrfScore, "vector");
        }

        // 处理 BM25 检索结果
        for (RankedDoc rankedDoc : bm25Results) {
            double rrfScore = sparseWeight / (rrfK + rankedDoc.rank);
            fusionScores.computeIfAbsent(rankedDoc.id, k -> new FusionScore(rankedDoc.document))
                    .addScore(rrfScore, "bm25");
        }

        // 按融合分数排序并返回 TopK
        return fusionScores.values().stream()
                .sorted((a, b) -> Double.compare(b.totalScore, a.totalScore))
                .limit(topK)
                .map(fs -> {
                    fs.document.getMetadata().put("fusion_score", fs.totalScore);
                    fs.document.getMetadata().put("sources", fs.sources);
                    return fs.document;
                })
                .collect(Collectors.toList());
    }

    /**
     * 降级：纯向量搜索
     */
    private List<Document> searchVectorOnly(String query, int topK) {
        logger.warn("Falling back to vector-only search");
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        return vectorStore.similaritySearch(request);
    }

    /**
     * 索引文档到 BM25（应在数据加载时调用）
     *
     * @param documents 文档列表
     */
    public void indexForBM25(List<Document> documents) {
        bm25SearchService.indexDocuments(documents);
    }

    /**
     * 追加文档到 BM25 索引
     *
     * @param documents 文档列表
     */
    public void addToBM25Index(List<Document> documents) {
        bm25SearchService.addDocuments(documents);
    }

    /**
     * 清空 BM25 索引（同时清除 Redis 持久化数据），供运维重建使用
     */
    public void clearBM25Index() {
        bm25SearchService.clearIndex();
    }

    /**
     * BM25 索引是否已构建
     */
    public boolean isBm25IndexBuilt() {
        return bm25SearchService.isIndexBuilt();
    }

    /**
     * 当前 BM25 内存索引文档数
     */
    public int getBm25DocumentCount() {
        return bm25SearchService.getDocumentCount();
    }

    /**
     * 内部类：带排名的文档
     */
    private static class RankedDoc {
        final String id;
        final Document document;
        final float score;
        final int rank;

        RankedDoc(String id, Document document, float score, int rank) {
            this.id = id;
            this.document = document;
            this.score = score;
            this.rank = rank;
        }
    }

    /**
     * 内部类：融合分数
     */
    private static class FusionScore {
        final Document document;
        double totalScore = 0.0;
        final List<String> sources = new ArrayList<>();

        FusionScore(Document document) {
            this.document = document;
        }

        void addScore(double score, String source) {
            this.totalScore += score;
            if (!sources.contains(source)) {
                sources.add(source);
            }
        }
    }
}
