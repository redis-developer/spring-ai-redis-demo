package com.redis.demo.spring.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import com.redis.demo.spring.ai.service.DocumentCountProvider;
import com.redis.demo.spring.ai.service.HybridDocumentService;
import com.redis.demo.spring.ai.util.TokenLimitEnforcer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagDataLoader 单元测试")
class RagDataLoaderTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentCountProvider documentCountProvider;

    @Mock
    private HybridDocumentService hybridDocumentService;

    @Mock
    private Resource dataResource;

    @Mock
    private ApplicationArguments applicationArguments;

    private RagDataLoader ragDataLoader;

    /**
     * 交给 mock 的 HybridDocumentService 返回、再断言收到的是同一批 Document；
     * 必须复用同一实例，否则 Document 的随机 ID 会让相等比较失败。
     */
    private List<Document> loadedDocuments;

    private static final String INDEX_NAME = "test-index";

    /** RagDataLoader.checkDbCollectionRecords 的跳过阈值：已有 >= 10 条就不再加载。 */
    private static final int SKIP_THRESHOLD = 10;

    private static final String JSON_DATA = """
        [
            {
                "name": "测试啤酒1",
                "abv": 5.0,
                "ibu": 20,
                "description": "这是一款测试啤酒的描述"
            },
            {
                "name": "测试啤酒2",
                "abv": 4.5,
                "ibu": 15,
                "description": "另一款测试啤酒的描述"
            }
        ]
        """;

    @BeforeEach
    void setUp() {
        ragDataLoader = new RagDataLoader(vectorStore, documentCountProvider, hybridDocumentService);
        ReflectionTestUtils.setField(ragDataLoader, "data", dataResource);
        ReflectionTestUtils.setField(ragDataLoader, "indexName", INDEX_NAME);
        loadedDocuments = List.of(
                new Document("这是一款测试啤酒的描述", Map.of("name", "测试啤酒1")),
                new Document("另一款测试啤酒的描述", Map.of("name", "测试啤酒2")));
    }

    @Test
    @DisplayName("当数据已存在时应跳过加载")
    void shouldSkipLoadingWhenDataAlreadyExists() throws Exception {
        // Given
        when(dataResource.exists()).thenReturn(true);
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(25000);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore, never()).add(anyList());
        verify(dataResource, never()).getInputStream();
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("当数据不存在时应成功加载普通JSON文件")
    void shouldLoadDataWhenNoExistingData() throws Exception {
        // Given
        stubJsonSource(0);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        List<Document> capturedDocuments = captureAddedDocuments();
        assertThat(capturedDocuments).isNotEmpty();
        assertThat(capturedDocuments).containsExactlyElementsOf(loadedDocuments);
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("单条超过 embedding 阈值时入库前应先被二次切分")
    void shouldSplitOversizedDocumentBeforeAdding() throws Exception {
        // Given：一条没有句子边界、远超阈值的大文档
        String oversized = "向量检索的重排序模型可以显著提升召回质量".repeat(600);
        assertThat(TokenLimitEnforcer.countTokens(oversized))
                .isGreaterThan(TokenLimitEnforcer.STRATEGY_TOKEN_LIMIT);
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(0);
        when(dataResource.exists()).thenReturn(true);
        when(dataResource.getFilename()).thenReturn("beers.json");
        when(hybridDocumentService.loadDocumentFromZip(any()))
                .thenReturn(List.of(new Document(oversized)));

        // When
        ragDataLoader.run(applicationArguments);

        // Then：入库的是切分后的多块，且每块都不顶穿阈值
        List<Document> capturedDocuments = captureAddedDocuments();
        assertThat(capturedDocuments).hasSizeGreaterThan(1);
        assertThat(capturedDocuments).allSatisfy(document -> assertThat(
                TokenLimitEnforcer.countTokens(document.getText()))
                        .isLessThanOrEqualTo(TokenLimitEnforcer.STRATEGY_TOKEN_LIMIT));
    }

    @Test
    @DisplayName("应正确处理GZIP压缩文件：解压后仍要交给 JSON reader")
    void shouldHandleGzipFile() throws Exception {
        // Given
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(0);
        when(hybridDocumentService.loadDocumentFromZip(any())).thenReturn(loadedDocuments);
        setupDataResourceMock(true, createGzipData(JSON_DATA));

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore).add(anyList());
        verify(dataResource).getFilename();
        verify(dataResource).getInputStream();
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("当文件读取失败时应抛出异常")
    void shouldThrowExceptionWhenFileReadFails() throws Exception {
        // Given：只有 gz/zip 分支才会真的去读数据流，所以用 .gz 文件名
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(0);
        when(dataResource.exists()).thenReturn(true);
        when(dataResource.getFilename()).thenReturn("beers.json.gz");
        when(dataResource.getInputStream()).thenThrow(new IOException("文件读取失败"));

        // When & Then
        assertThatThrownBy(() -> ragDataLoader.run(applicationArguments))
            .isInstanceOf(IOException.class)
            .hasMessage("文件读取失败");
    }

    @Test
    @DisplayName("当向量存储操作失败时应抛出异常")
    void shouldThrowExceptionWhenVectorStoreOperationFails() throws Exception {
        // Given
        stubJsonSource(0);
        doThrow(new RuntimeException("向量存储操作失败"))
            .when(vectorStore).add(anyList());

        // When & Then
        assertThatThrownBy(() -> ragDataLoader.run(applicationArguments))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("向量存储操作失败");
    }

    @Test
    @DisplayName("应正确处理文档数量为0的情况")
    void shouldHandleZeroDocumentCount() throws Exception {
        // Given
        stubJsonSource(0);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore).add(anyList());
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("已有文档数低于阈值时应继续加载")
    void shouldLoadWhenDocumentCountBelowThreshold() throws Exception {
        // Given
        stubJsonSource(SKIP_THRESHOLD - 1);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore).add(anyList());
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("已有文档数达到阈值边界时应跳过加载")
    void shouldSkipWhenDocumentCountReachesThreshold() throws Exception {
        // Given
        when(dataResource.exists()).thenReturn(true);
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(SKIP_THRESHOLD);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore, never()).add(anyList());
        verify(hybridDocumentService, never()).loadDocumentFromZip(any());
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("当DocumentCountProvider不支持操作时应继续加载数据")
    void shouldContinueLoadingWhenDocumentCountProviderNotSupported() throws Exception {
        // Given
        when(dataResource.exists()).thenReturn(true);
        when(dataResource.getFilename()).thenReturn("beers.json");
        when(documentCountProvider.getDocumentCount(INDEX_NAME))
            .thenThrow(new UnsupportedOperationException("当前VectorStore实现不支持文档数量查询"));
        when(hybridDocumentService.loadDocumentFromZip(any())).thenReturn(loadedDocuments);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore).add(anyList());
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    /**
     * 普通 JSON 分支的通用打桩：已有文档数 + 读出的文档列表
     */
    private void stubJsonSource(int existingDocuments) throws IOException {
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(existingDocuments);
        when(dataResource.exists()).thenReturn(true);
        when(dataResource.getFilename()).thenReturn("beers.json");
        when(hybridDocumentService.loadDocumentFromZip(any())).thenReturn(loadedDocuments);
    }

    @SuppressWarnings("unchecked")
    private List<Document> captureAddedDocuments() {
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        return documentsCaptor.getValue();
    }

    /**
     * 设置数据资源Mock对象
     */
    private void setupDataResourceMock(boolean isGzipFile, Object data) throws IOException {
        when(dataResource.exists()).thenReturn(true);
        String filename = isGzipFile ? "beers.json.gz" : "beers.json";
        when(dataResource.getFilename()).thenReturn(filename);

        InputStream inputStream;
        if (data instanceof String) {
            inputStream = new ByteArrayInputStream(((String) data).getBytes());
        } else if (data instanceof byte[]) {
            inputStream = new ByteArrayInputStream((byte[]) data);
        } else {
            throw new IllegalArgumentException("不支持的数据类型");
        }

        when(dataResource.getInputStream()).thenReturn(inputStream);
    }

    /**
     * 创建GZIP压缩数据
     */
    private byte[] createGzipData(String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(content.getBytes());
        }
        return baos.toByteArray();
    }

}
