package com.redis.demo.spring.ai.model;

/**
 * BM25 索引状态自检响应 DTO。
 * 汇总内存 / Redis / Milvus 三处计数与健康、一致性判定，供运维定位
 * “BM25 静默退化为纯向量”等问题。
 */
public record Bm25StatusResponse(
        boolean hybridEnabled,
        boolean indexBuilt,
        int memCount,
        boolean redisConfigured,
        boolean redisAvailable,
        long redisCount,
        int milvusCount,
        boolean healthy,
        boolean consistentWithMilvus,
        String recommendation) {
}
