package com.redis.demo.spring.ai.model;

/**
 * BM25 索引重建响应 DTO。
 * 成功时携带重建后的文档数，失败时携带错误信息。
 */
public record Bm25RebuildResponse(
        boolean success,
        Integer bm25DocumentCount,
        String message,
        String error) {

    public static Bm25RebuildResponse success(int count) {
        String message = count > 0 ? "BM25 index rebuilt" : "No documents loaded from data source";
        return new Bm25RebuildResponse(true, count, message, null);
    }

    public static Bm25RebuildResponse failure(String error) {
        return new Bm25RebuildResponse(false, null, null, error);
    }
}
