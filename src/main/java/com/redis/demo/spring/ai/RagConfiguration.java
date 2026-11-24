package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.service.BM25DocumentPersistenceService;
import com.redis.demo.spring.ai.service.BM25SearchService;
import com.redis.demo.spring.ai.service.HybridSearchService;
import com.redis.demo.spring.ai.service.RagService;
import com.redis.demo.spring.ai.service.RerankService;
import com.redis.demo.spring.ai.util.OllamaRestClientFactory;
import io.micrometer.observation.ObservationRegistry;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class RagConfiguration {

    @Value("${spring.ai.vectorstore.pinecone.index-name}")
    private String indexName;

    /** 上游 HTTP 连接超时，毫秒 */
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    /** 上游 HTTP 读取超时，毫秒 */
    private static final int READ_TIMEOUT_MILLIS = 60_000;

    /**
     * 上游 HTTP 客户端超时配置。
     * 层级关系必须保持：HTTP 读取超时(60s) &lt; RagController 业务超时(65s)
     * &lt; spring.mvc.async.request-timeout(90s)，否则最外层的容器超时会先把响应变成 503。
     */
    @Bean
    public RestClientCustomizer restClientCustomizer() {
        // 原先写成 Duration.ofSeconds(60).toMillisPart()：toMillisPart() 只取「秒以外的毫秒」，
        // 整分钟返回 0，等于不限超时，上游卡住时线程会一直阻塞。
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return restClientBuilder -> restClientBuilder.requestFactory(requestFactory);
    }

    /**
     * Ollama embedding 模型。
     *
     * 注意：OllamaApi 内部使用的是静态 RestClient.builder()，不经过容器里的
     * restClientCustomizer；Boot 4.2 起该默认工厂变为 Reactor Netty 并附带约 10s 读超时，
     * 而本地 bge-m3 一次批量 embed 就要 40s 以上，会把 RagDataLoader 的启动向量化直接打断。
     * 因此这里显式注入带超时的 RestClient.Builder，embedding 读超时默认放宽到 300s
     * （它不在 /chat 请求路径上，不影响 60s &lt; 65s &lt; 90s 的聊天超时层级）。
     */
    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${spring.ai.ollama.embedding.options.model}") String model,
            @Value("${spring.ai.ollama.embedding.connect-timeout:10s}") Duration connectTimeout,
            @Value("${spring.ai.ollama.embedding.read-timeout:300s}") Duration readTimeout) {
        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(OllamaRestClientFactory.restClientBuilder(connectTimeout, readTimeout))
                .build();
        return new OllamaEmbeddingModel(ollamaApi,
                OllamaEmbeddingOptions.builder().model(model).build(),
                ObservationRegistry.create(),
                ModelManagementOptions.builder().timeout(Duration.ofSeconds(30)).build());
    }

    @Bean
    public MilvusServiceClient milvusServiceClient(@Value("${spring.ai.vectorstore.milvus.client.host}") String host) {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(19530)
                .withConnectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withKeepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .withKeepAliveTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withIdleTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        return new MilvusServiceClient(connectParam);
    }

    @Bean
    @Primary
    public VectorStore milvusVectorStore(MilvusServiceClient client,
            @Value("${spring.ai.vectorstore.milvus.collection-name}") String collectionName,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.milvus.embeddingDimension:1536}") int dimension) {
        return MilvusVectorStore.builder(client, embeddingModel)
                .collectionName(collectionName)
                .databaseName("default")
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .embeddingDimension(dimension)
                .batchingStrategy(new TokenCountBatchingStrategy())
                .initializeSchema(true)
                .build();
    }

    /**
     * RerankService Bean - 仅当 rerank.enabled=true 时创建
     */
    @Bean
    @ConditionalOnProperty(name = "rerank.enabled", havingValue = "true")
    public RerankService rerankService(
            @Value("${SILICONFLOW_KEY:${siliconflow.api-key:}}") String apiKey,
            @Value("${rerank.model:BAAI/bge-reranker-v2-m3}") String rerankModel) {
        return new RerankService(apiKey, rerankModel);
    }

    /**
     * BM25DocumentPersistenceService Bean - 仅当 hybrid.search.enabled=true 且 Redis
     * 可用时创建
     * 用于将 BM25 文档数据持久化到 Redis Cloud
     */
    @Bean
    @ConditionalOnProperty(name = "hybrid.search.enabled", havingValue = "true")
    public BM25DocumentPersistenceService bm25DocumentPersistenceService(
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            return new BM25DocumentPersistenceService(redisTemplate);
        }
        return null;
    }

    /**
     * BM25SearchService Bean - 仅当 hybrid.search.enabled=true 时创建
     * Lucene 内存索引，支持 Redis 持久化
     */
    @Bean
    @ConditionalOnProperty(name = "hybrid.search.enabled", havingValue = "true")
    public BM25SearchService bm25SearchService(
            org.springframework.beans.factory.ObjectProvider<BM25DocumentPersistenceService> persistenceServiceProvider) {
        BM25SearchService service = new BM25SearchService();
        BM25DocumentPersistenceService persistenceService = persistenceServiceProvider.getIfAvailable();
        if (persistenceService != null) {
            service.setPersistenceService(persistenceService);
            // 尝试从 Redis 恢复索引
            service.restoreFromPersistence();
        }
        return service;
    }

    /**
     * HybridSearchService Bean - 仅当 hybrid.search.enabled=true 时创建
     * 结合 Lucene BM25 和 Milvus 向量检索，使用 RRF 融合
     */
    @Bean
    @ConditionalOnProperty(name = "hybrid.search.enabled", havingValue = "true")
    public HybridSearchService hybridSearchService(
            VectorStore vectorStore,
            BM25SearchService bm25SearchService) {
        return new HybridSearchService(vectorStore, bm25SearchService);
    }

    /**
     * RagService Bean - 带可选的 RerankService 和 HybridSearchService
     */
    @Bean
    public RagService ragService(ChatModel chatModel, VectorStore vectorStore,
            @Value("${rerank.enabled:false}") boolean rerankEnabled,
            @Value("${hybrid.search.enabled:false}") boolean hybridSearchEnabled,
            org.springframework.beans.factory.ObjectProvider<RerankService> rerankServiceProvider,
            org.springframework.beans.factory.ObjectProvider<HybridSearchService> hybridSearchServiceProvider) {
        RerankService rerankService = rerankEnabled ? rerankServiceProvider.getIfAvailable() : null;
        HybridSearchService hybridSearchService = hybridSearchEnabled
                ? hybridSearchServiceProvider.getIfAvailable()
                : null;
        return new RagService(chatModel, vectorStore, rerankService, hybridSearchService);
    }

}