package com.redis.demo.spring.ai.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BM25 文档持久化服务
 * 将文档数据存储到 Redis Cloud，支持应用重启后恢复 BM25 索引
 * 当 Redis 内存不足时，自动降级为内存模式（不持久化）
 *
 * @author Spring AI RAG Demo
 * @since 2024-12
 */
public class BM25DocumentPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(BM25DocumentPersistenceService.class);

    // Redis Key 前缀
    private static final String KEY_PREFIX = "bm25:doc:";
    private static final String INDEX_KEY = "bm25:index:docs";

    // 文本最大长度限制（减少内存占用）
    private static final int MAX_TEXT_LENGTH = 2000;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis 可用状态标记（OOM 后禁用）
    private volatile boolean redisAvailable = true;

    public BM25DocumentPersistenceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = JsonMapper.builder().build();
    }

    /**
     * 保存文档列表到 Redis（带 OOM 保护）
     * 如果Redis中已存在文档数据，则跳过保存
     *
     * @param documents 文档列表
     * @return 是否保存成功
     */
    public boolean saveDocuments(List<Document> documents) {
        if (!redisAvailable) {
            logger.warn("Redis persistence is disabled due to previous OOM error");
            return false;
        }

        if (documents == null || documents.isEmpty()) {
            logger.warn("No documents to save");
            return false;
        }

        // 检查Redis中是否已存在文档数据
        long existingCount = getDocumentCount();
        if (existingCount > 0) {
            logger.info("BM25 documents already exist in Redis ({} documents), skipping save to avoid duplicate data", 
                    existingCount);
            return true; // 返回true表示数据已存在，不需要重新保存
        }

        logger.info("Saving {} documents to Redis for BM25 persistence", documents.size());

        try {
            // 清除旧数据（虽然应该没有，但为了安全起见）
            clearDocuments();

            // 保存每个文档（使用压缩策略）
            int savedCount = 0;
            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                String docId = doc.getId() != null ? doc.getId() : "doc_" + i;

                if (saveOneDocument(docId, doc)) {
                    savedCount++;
                } else {
                    // 遇到 OOM，停止保存更多文档
                    logger.warn("Stopped saving at document {} due to Redis OOM", i);
                    break;
                }
            }

            logger.info("Successfully saved {}/{} documents to Redis", savedCount, documents.size());
            return savedCount > 0;

        } catch (Exception e) {
            handleRedisError(e, "save documents");
            return false;
        }
    }

    /**
     * 保存单个文档
     */
    private boolean saveOneDocument(String docId, Document doc) {
        try {
            String key = KEY_PREFIX + docId;

            // 压缩文档数据：只保存必要字段，截断过长文本
            Map<String, Object> docData = new HashMap<>();
            docData.put("id", docId);
            docData.put("text", truncateText(doc.getText()));
            // 不保存完整 metadata，减少存储空间

            String jsonValue = objectMapper.writeValueAsString(docData);
            redisTemplate.opsForValue().set(key, jsonValue);
            redisTemplate.opsForSet().add(INDEX_KEY, docId);
            return true;

        } catch (DataAccessException e) {
            if (isOomError(e)) {
                handleOomError();
                return false;
            }
            throw e;
        } catch (JacksonException e) {
            logger.error("Failed to serialize document {}: {}", docId, e.getMessage());
            return false;
        }
    }

    /**
     * 截断过长文本以减少存储空间
     */
    private String truncateText(String text) {
        if (text == null)
            return "";
        if (text.length() <= MAX_TEXT_LENGTH)
            return text;
        return text.substring(0, MAX_TEXT_LENGTH) + "...";
    }

    /**
     * 检测是否为 OOM 错误
     */
    private boolean isOomError(Exception e) {
        String message = e.getMessage();
        if (message != null && message.contains("OOM")) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && causeMsg.contains("OOM")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 处理 OOM 错误：禁用 Redis 持久化
     */
    private void handleOomError() {
        redisAvailable = false;
        logger.error("Redis OOM detected! Disabling Redis persistence. " +
                "BM25 index will only be stored in memory. " +
                "Consider upgrading your Redis Cloud plan for persistence support.");
    }

    /**
     * 处理 Redis 通用错误
     */
    private void handleRedisError(Exception e, String operation) {
        if (isOomError(e)) {
            handleOomError();
        } else {
            logger.error("Redis error during {}: {}", operation, e.getMessage());
        }
    }

    /**
     * 从 Redis 加载所有文档
     *
     * @return 文档列表
     */
    public List<Document> loadDocuments() {
        List<Document> documents = new ArrayList<>();

        if (!redisAvailable) {
            return documents;
        }

        try {
            Set<String> docIds = redisTemplate.opsForSet().members(INDEX_KEY);
            if (docIds == null || docIds.isEmpty()) {
                logger.info("No documents found in Redis");
                return documents;
            }

            logger.info("Loading {} documents from Redis", docIds.size());

            for (String docId : docIds) {
                String key = KEY_PREFIX + docId;
                String jsonValue = redisTemplate.opsForValue().get(key);

                if (jsonValue != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> docData = objectMapper.readValue(jsonValue, Map.class);

                    String text = (String) docData.get("text");
                    Map<String, Object> metadata = new HashMap<>();

                    Document doc = new Document(text, metadata);
                    documents.add(doc);
                }
            }

            logger.info("Successfully loaded {} documents from Redis", documents.size());

        } catch (Exception e) {
            handleRedisError(e, "load documents");
        }

        return documents;
    }

    /**
     * 清除 Redis 中的所有文档数据
     */
    public void clearDocuments() {
        if (!redisAvailable)
            return;

        try {
            Set<String> docIds = redisTemplate.opsForSet().members(INDEX_KEY);
            if (docIds != null && !docIds.isEmpty()) {
                for (String docId : docIds) {
                    redisTemplate.delete(KEY_PREFIX + docId);
                }
                redisTemplate.delete(INDEX_KEY);
                logger.info("Cleared {} documents from Redis", docIds.size());
            }
        } catch (Exception e) {
            handleRedisError(e, "clear documents");
        }
    }

    /**
     * 获取 Redis 中存储的文档数量
     */
    public long getDocumentCount() {
        if (!redisAvailable)
            return 0;
        try {
            Long count = redisTemplate.opsForSet().size(INDEX_KEY);
            return count != null ? count : 0;
        } catch (Exception e) {
            handleRedisError(e, "get document count");
            return 0;
        }
    }

    /**
     * 检查 Redis 中是否有持久化的文档
     */
    public boolean hasPersistedDocuments() {
        return redisAvailable && getDocumentCount() > 0;
    }

    /**
     * 追加文档到 Redis（带 OOM 保护）
     */
    public boolean appendDocuments(List<Document> documents) {
        if (!redisAvailable || documents == null || documents.isEmpty()) {
            return false;
        }

        logger.info("Appending {} documents to Redis", documents.size());

        try {
            long existingCount = getDocumentCount();
            int savedCount = 0;

            for (int i = 0; i < documents.size(); i++) {
                Document doc = documents.get(i);
                String docId = doc.getId() != null ? doc.getId() : "doc_" + (existingCount + i);

                if (saveOneDocument(docId, doc)) {
                    savedCount++;
                } else {
                    break;
                }
            }

            logger.info("Successfully appended {}/{} documents to Redis", savedCount, documents.size());
            return savedCount > 0;

        } catch (Exception e) {
            handleRedisError(e, "append documents");
            return false;
        }
    }

    /**
     * 检查 Redis 是否可用
     */
    public boolean isRedisAvailable() {
        return redisAvailable;
    }

    /**
     * 重新启用 Redis（手动恢复）
     */
    public void enableRedis() {
        redisAvailable = true;
        logger.info("Redis persistence re-enabled");
    }
}
