package com.redis.demo.spring.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import com.redis.demo.spring.ai.service.BM25SearchService;
import com.redis.demo.spring.ai.service.DocumentCountProvider;
import com.redis.demo.spring.ai.service.HybridDocumentService;
import com.redis.demo.spring.ai.service.HybridSearchService;

/**
 * BM25 索引重建集成测试（方案B运维能力）。
 *
 * <p>串联真实的 {@link RagDataLoader} + {@link HybridSearchService} + {@link BM25SearchService}
 * （基于真实 Lucene 内存索引），仅 mock 外部 I/O（Milvus / Redis / 原始文件源），
 * 用于验证“Milvus 已有数据但 BM25 索引缺失”场景下，通过 rebuild 重建出可用且幂等的 BM25 索引。
 * 不依赖 Docker，可确定性复现。
 */
@DisplayName("BM25 索引重建集成测试")
class Bm25IndexRecoveryIntegrationTest {

	private static final String COLLECTION = "test_collection";

	private RagDataLoader ragDataLoader;
	private BM25SearchService bm25SearchService;
	private HybridSearchService hybridSearchService;
	private VectorStore vectorStore;
	private HybridDocumentService hybridDocumentService;
	private Resource dataResource;

	private List<Document> sampleDocs() {
		return Arrays.asList(
				new Document("Spring AI 是一个用于构建 AI 应用的框架"),
				new Document("Milvus 是一个开源的高性能向量数据库"),
				new Document("BM25 全文检索算法用于关键词相关性评分"));
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws Exception {
		vectorStore = mock(VectorStore.class);
		hybridDocumentService = mock(HybridDocumentService.class);
		DocumentCountProvider documentCountProvider = mock(DocumentCountProvider.class);

		// 真实的 Lucene 支撑的 BM25 服务（无 Redis 持久化 => 内存模式）
		bm25SearchService = new BM25SearchService();
		hybridSearchService = new HybridSearchService(vectorStore, bm25SearchService);

		ragDataLoader = new RagDataLoader(vectorStore, documentCountProvider, hybridDocumentService);
		ReflectionTestUtils.setField(ragDataLoader, "hybridSearchService", hybridSearchService);
		ReflectionTestUtils.setField(ragDataLoader, "indexName", COLLECTION);

		// 模拟原始数据源为 PDF（走 loadDocDirect 分支），返回固定文档集合
		dataResource = mock(Resource.class);
		when(dataResource.exists()).thenReturn(true);
		when(dataResource.getFilename()).thenReturn("paper.pdf");
		when(hybridDocumentService.loadDocDirect(dataResource)).thenReturn(sampleDocs());

		ReflectionTestUtils.setField(ragDataLoader, "data", dataResource);
	}

	@Test
	@DisplayName("rebuild：从数据源重建出可用的 BM25 索引且不触碰 Milvus")
	void rebuild_shouldBuildUsableIndexAndNotWriteVectorStore() throws Exception {
		// Given：初始未建索引
		assertFalse(bm25SearchService.isIndexBuilt(), "初始索引应未构建");

		// When
		int count = ragDataLoader.rebuildBm25FromSource();

		// Then：索引重建成功且可检索
		assertEquals(3, count, "重建后应包含3个文档");
		assertTrue(bm25SearchService.isIndexBuilt(), "重建后索引应已构建");
		assertEquals(3, bm25SearchService.getDocumentCount(), "内存索引文档数应为3");

		List<BM25SearchService.ScoredDocument> results = bm25SearchService.search("BM25 检索", 3);
		assertFalse(results.isEmpty(), "重建后 BM25 搜索应返回结果");

		// 数据来源被读取一次
		verify(hybridDocumentService, times(1)).loadDocDirect(dataResource);
		// 关键：重建 BM25 不应重复写入 VectorStore（避免重复 embedding / 重复索引）
		verify(vectorStore, never()).add(anyList());
	}

	@Test
	@DisplayName("rebuild：幂等——重复重建不产生重复文档")
	void rebuild_shouldBeIdempotentWithoutDuplicates() throws Exception {
		// When：连续重建两次
		ragDataLoader.rebuildBm25FromSource();
		int secondCount = ragDataLoader.rebuildBm25FromSource();

		// Then：清空后重建，文档数保持3而不是6
		assertEquals(3, secondCount, "二次重建结果数应仍为3");
		assertEquals(3, bm25SearchService.getDocumentCount(), "内存索引不应翻倍（无重复文档）");
	}

	@Test
	@DisplayName("rebuild：数据源为空时安全返回0，不误标索引已建")
	void rebuild_shouldReturnZeroWhenSourceEmpty() throws Exception {
		// Given：数据源解析不出文档
		when(hybridDocumentService.loadDocDirect(dataResource)).thenReturn(Collections.emptyList());

		// When
		int count = ragDataLoader.rebuildBm25FromSource();

		// Then
		assertEquals(0, count, "空数据源应返回0");
		assertFalse(bm25SearchService.isIndexBuilt(), "空数据源不应把索引标记为已建");
		verify(vectorStore, never()).add(anyList());
	}

	@Test
	@DisplayName("rebuild：混合检索未启用（无 HybridSearchService）时抛出明确异常")
	void rebuild_shouldThrowWhenHybridDisabled() {
		// Given：hybrid 关闭，hybridSearchService 为 null
		ReflectionTestUtils.setField(ragDataLoader, "hybridSearchService", null);

		// When & Then
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> ragDataLoader.rebuildBm25FromSource());
		assertNotNull(ex.getMessage());
		assertTrue(ex.getMessage().contains("hybrid.search.enabled"), "异常信息应指明混合检索被禁用");
	}

	@Test
	@DisplayName("委托：HybridSearchService 的 BM25 状态查询与真实索引一致")
	void delegates_shouldReflectRealIndexState() throws Exception {
		assertFalse(hybridSearchService.isBm25IndexBuilt(), "初始应未建");
		assertEquals(0, hybridSearchService.getBm25DocumentCount(), "初始文档数应为0");

		ragDataLoader.rebuildBm25FromSource();

		assertTrue(hybridSearchService.isBm25IndexBuilt(), "重建后委托查询应反映已建");
		assertEquals(3, hybridSearchService.getBm25DocumentCount(), "委托计数应为3");

		hybridSearchService.clearBM25Index();
		assertFalse(hybridSearchService.isBm25IndexBuilt(), "清空后应未建");
		assertEquals(0, hybridSearchService.getBm25DocumentCount(), "清空后计数应为0");
	}
}
