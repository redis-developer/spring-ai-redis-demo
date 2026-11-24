package com.redis.demo.spring.ai.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rerank 服务
 * 使用硅基流动 (SiliconFlow) 的 BAAI/bge-reranker-v2-m3 模型对文档进行精排
 * API 文档: https://docs.siliconflow.cn/cn/api-reference/rerank/create-rerank
 */
public class RerankService {

    private static final Logger logger = LoggerFactory.getLogger(RerankService.class);

    /**
     * 硅基流动 Rerank API 地址
     */
    private static final String SILICONFLOW_RERANK_URL = "https://api.siliconflow.cn/v1/rerank";

    /**
     * Rerank 接口超时（毫秒）。默认 RestTemplate 不限超时，一次卡住会把整个
     * /chat 异步请求拖到容器超时（表现为 503），这里让它快速失败并降级为原序。
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private static final int READ_TIMEOUT_MILLIS = 20_000;

    private final String apiKey;
    private final String rerankModel;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数
     * 
     * @param apiKey      硅基流动 API Key (从环境变量 SILICONFLOW_KEY 获取)
     * @param rerankModel 重排序模型名称，默认 BAAI/bge-reranker-v2-m3
     */
    public RerankService(String apiKey, String rerankModel) {
        this.apiKey = apiKey;
        this.rerankModel = rerankModel;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        this.restTemplate = new RestTemplate(requestFactory);
        this.objectMapper = JsonMapper.builder().build();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("Siliconflow API Key is empty! Please set SILICONFLOW_KEY environment variable.");
        }
    }

    /**
     * 对文档列表进行 rerank 精排
     *
     * @param query     用户查询
     * @param documents 待排序的文档列表
     * @param topN      返回的 Top N 文档数量
     * @return 精排后的文档列表（按相关性降序）
     */
    public List<Document> rerank(String query, List<Document> documents, int topN) {
        if (documents == null || documents.isEmpty()) {
            logger.debug("No documents to rerank");
            return new ArrayList<>();
        }

        logger.info("Starting rerank for {} documents with topN={}", documents.size(), topN);

        try {
            // 构建文档文本列表
            List<String> documentTexts = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.toList());

            // 调用硅基流动 Rerank API
            List<RerankResult> results = callSiliconflowRerankApi(query, documentTexts, documents, topN);

            // 提取精排后的文档
            List<Document> rerankedDocs = results.stream()
                    .map(RerankResult::getDocument)
                    .collect(Collectors.toList());

            logger.info("Rerank completed. Returning top {} documents from {} candidates",
                    rerankedDocs.size(), documents.size());

            // 日志输出精排结果
            for (int i = 0; i < results.size(); i++) {
                RerankResult r = results.get(i);
                String textPreview = r.getDocument().getText();
                textPreview = textPreview.substring(0, Math.min(100, textPreview.length()));
                logger.debug("Rank {}: score={}, text={}", i + 1, r.getRelevanceScore(), textPreview);
            }

            return rerankedDocs;

        } catch (Exception e) {
            logger.error("Failed to rerank documents, returning original order: {}", e.getMessage(), e);
            // 降级处理：返回原始顺序的前 topN 个文档
            return documents.stream().limit(topN).collect(Collectors.toList());
        }
    }

    /**
     * 调用硅基流动 Rerank API
     */
    private List<RerankResult> callSiliconflowRerankApi(String query, List<String> documentTexts,
            List<Document> originalDocuments, int topN) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // 构造请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", rerankModel);
        requestBody.put("query", query);
        requestBody.put("documents", documentTexts);
        requestBody.put("top_n", topN);
        requestBody.put("return_documents", false); // 不需要返回文档文本，我们已有原始文档

        String requestJson = objectMapper.writeValueAsString(requestBody);
        logger.debug("Rerank request: {}", requestJson);

        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(SILICONFLOW_RERANK_URL, entity, String.class);

        if (response.getBody() == null) {
            throw new RuntimeException("Empty response from Siliconflow Rerank API");
        }

        logger.debug("Rerank response: {}", response.getBody());

        // 解析响应
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        JsonNode resultsNode = jsonNode.get("results");

        if (resultsNode == null || !resultsNode.isArray()) {
            throw new RuntimeException("Invalid response format: missing 'results' array");
        }

        List<RerankResult> rerankResults = new ArrayList<>();
        for (JsonNode resultNode : resultsNode) {
            int index = resultNode.get("index").asInt();
            double relevanceScore = resultNode.get("relevance_score").asDouble();

            if (index >= 0 && index < originalDocuments.size()) {
                rerankResults.add(new RerankResult(originalDocuments.get(index), relevanceScore));
            }
        }

        // 按相关性分数降序排序（API 返回的可能已排序，但保险起见再排一次）
        rerankResults.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));

        return rerankResults;
    }
}
