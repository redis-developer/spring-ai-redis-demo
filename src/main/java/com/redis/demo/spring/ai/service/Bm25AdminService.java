package com.redis.demo.spring.ai.service;

import com.redis.demo.spring.ai.RagDataLoader;
import com.redis.demo.spring.ai.model.Bm25RebuildResponse;
import com.redis.demo.spring.ai.model.Bm25StatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * BM25 索引运维服务。
 *
 * <p>将“状态自检 + 索引重建”的编排逻辑收敛到独立的 Spring Bean，返回强类型 DTO，
 * 控制器只负责委托与 HTTP 状态映射，避免在控制器中拼装 {@code Map<String,Object>}。
 *
 * <p>{@code BM25SearchService} 与 {@code BM25DocumentPersistenceService} 均为
 * {@code hybrid.search.enabled=true} 时的条件 Bean，这里通过 {@link ObjectProvider}
 * 惰性获取，未启用时对应状态返回缺省值而非抛异常。
 */
@Service
public class Bm25AdminService {

    private static final Logger logger = LoggerFactory.getLogger(Bm25AdminService.class);

    private final ObjectProvider<BM25SearchService> bm25ServiceProvider;
    private final ObjectProvider<BM25DocumentPersistenceService> persistenceServiceProvider;
    private final DocumentCountProvider documentCountProvider;
    private final RagDataLoader ragDataLoader;
    private final String collectionName;

    public Bm25AdminService(
            ObjectProvider<BM25SearchService> bm25ServiceProvider,
            ObjectProvider<BM25DocumentPersistenceService> persistenceServiceProvider,
            DocumentCountProvider documentCountProvider,
            RagDataLoader ragDataLoader,
            @Value("${spring.ai.vectorstore.milvus.collection-name}") String collectionName) {
        this.bm25ServiceProvider = bm25ServiceProvider;
        this.persistenceServiceProvider = persistenceServiceProvider;
        this.documentCountProvider = documentCountProvider;
        this.ragDataLoader = ragDataLoader;
        this.collectionName = collectionName;
    }

    /**
     * 汇总内存 / Redis / Milvus 三处计数，产出带健康与一致性判定的状态响应。
     */
    public Bm25StatusResponse status() {
        BM25SearchService bm25 = bm25ServiceProvider.getIfAvailable();
        boolean hybridEnabled = bm25 != null;
        boolean indexBuilt = bm25 != null && bm25.isIndexBuilt();
        int memCount = bm25 != null ? bm25.getDocumentCount() : 0;

        BM25DocumentPersistenceService persistence = persistenceServiceProvider.getIfAvailable();
        boolean redisConfigured = persistence != null;
        boolean redisAvailable = persistence != null && persistence.isRedisAvailable();
        long redisCount = persistence != null ? persistence.getDocumentCount() : 0L;

        int milvusCount = queryMilvusCount();

        boolean healthy = indexBuilt && memCount > 0;
        boolean consistent = healthy && milvusCount > 0
                && Math.abs(memCount - milvusCount) <= Math.max(1, milvusCount / 20);
        String recommendation = buildRecommendation(hybridEnabled, healthy, consistent, milvusCount, memCount);

        return new Bm25StatusResponse(hybridEnabled, indexBuilt, memCount, redisConfigured, redisAvailable,
                redisCount, milvusCount, healthy, consistent, recommendation);
    }

    /**
     * 触发 BM25 索引重建，捕获异常并转换为结构化响应，避免将堆栈暴露到 HTTP 层。
     */
    public Bm25RebuildResponse rebuild() {
        try {
            int count = ragDataLoader.rebuildBm25FromSource();
            return Bm25RebuildResponse.success(count);
        } catch (Exception e) {
            logger.error("BM25 rebuild failed: {}", e.getMessage(), e);
            return Bm25RebuildResponse.failure(e.getMessage());
        }
    }

    private int queryMilvusCount() {
        try {
            return documentCountProvider.getDocumentCount(collectionName);
        } catch (Exception e) {
            logger.warn("Failed to query Milvus document count: {}", e.getMessage());
            return -1;
        }
    }

    private String buildRecommendation(boolean hybridEnabled, boolean healthy, boolean consistent,
            int milvusCount, int memCount) {
        if (!hybridEnabled) {
            return "hybrid.search.enabled=false, BM25 disabled";
        }
        if (!healthy) {
            if (milvusCount > 0) {
                return "BM25 index missing while Milvus has data; call POST /admin/bm25/rebuild";
            }
            return "no data loaded yet";
        }
        if (!consistent) {
            return "BM25 count (" + memCount + ") diverges from Milvus (" + milvusCount
                    + "); consider POST /admin/bm25/rebuild";
        }
        return "OK";
    }
}
