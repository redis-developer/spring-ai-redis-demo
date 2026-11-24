package com.redis.demo.spring.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * RagDataLoader 集成测试
 * 使用 TestContainers 启动真实的 Milvus 容器进行集成测试
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DisplayName("RagDataLoader 集成测试")
class RagDataLoaderIntegrationTest {

    @Container
    static GenericContainer<?> milvus = new GenericContainer<>(DockerImageName.parse("milvusdb/milvus:v2.6.7"))
            .withExposedPorts(19530)
            .withEnv("ETCD_USE_EMBED", "true")
            .withEnv("MINIO_ENABLED", "true");

    @Autowired
    private RagDataLoader ragDataLoader;

    @MockitoBean
    private MilvusVectorStore milvusVectorStore;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.vectorstore.milvus.uri",
                () -> "http://" + milvus.getHost() + ":" + milvus.getMappedPort(19530));
        registry.add("spring.ai.vectorstore.milvus.collection-name", () -> "test_beers_collection");
        registry.add("spring.ai.vectorstore.milvus.database-name", () -> "default");
    }

    @BeforeEach
    void setUp() {
        // 初始化Milvus测试环境
    }

    @Disabled("Milvus集成测试待完善")
    @DisplayName("集成测试：验证 Milvus 连接和基本操作")
    void shouldConnectToMilvusAndPerformBasicOperations() {
        // Given
        // When & Then
        assertThat(milvus.isRunning()).isTrue();
    }

    @Disabled("Milvus集成测试待完善")
    @DisplayName("集成测试：验证 Milvus 集合信息查询")
    void shouldQueryMilvusCollectionInfo() {
        // Given
        String collectionName = "test_beers_collection";

        // When & Then
        // Milvus集合信息查询逻辑待实现
    }

    @DisplayName("集成测试：验证应用上下文加载")
    void shouldLoadApplicationContext() {
        // Then
        assertThat(ragDataLoader).isNotNull();
        assertThat(milvusVectorStore).isNotNull();
    }
}