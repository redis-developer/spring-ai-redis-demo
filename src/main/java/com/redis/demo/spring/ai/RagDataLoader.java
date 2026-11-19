package com.redis.demo.spring.ai;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import com.redis.demo.spring.ai.service.DocumentCountProvider;
import com.redis.demo.spring.ai.service.HybridDocumentService;
import com.redis.demo.spring.ai.service.HybridSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class RagDataLoader implements ApplicationRunner {

	// 使用一个示例数据URL替代原来的无效URL
	public static final String DATA_BEERS_JSON_GZ = "https://arxiv.org/pdf/2506.10380";
	private static final Logger logger = LoggerFactory.getLogger(RagDataLoader.class);

	// 定义关键字数组
	public static final String[] KEYS = { "name", "abv", "ibu", "description" };

	// 获取数据资源
	@Value("classpath:/data/beers.json.gz")
	private Resource data;

	// 获取索引名称
	@Value("${spring.ai.vectorstore.milvus.collection-name}")
	private String indexName;

	// 定义VectorStore实例
	private final VectorStore vectorStore;

	private final HybridDocumentService hybridDocumentService;

	// 文档数量提供者
	private final DocumentCountProvider documentCountProvider;

	// 混合检索服务（可选，用于 BM25 索引构建）
	@Autowired(required = false)
	private HybridSearchService hybridSearchService;

	// 构造函数，注入VectorStore实例和文档数量提供者
	public RagDataLoader(VectorStore vectorStore, DocumentCountProvider documentCountProvider,
			HybridDocumentService hybridDocumentService) {
		this.vectorStore = vectorStore;
		this.documentCountProvider = documentCountProvider;
		this.hybridDocumentService = hybridDocumentService;
	}

	// 在RagDataLoader类中添加批次大小常量
	private static final int BATCH_SIZE = 32;

	// 在 RagDataLoader 类中添加文本处理方法
	// 在 RagDataLoader 类中添加以下方法
	private static final int MAX_TOKENS = 512;
	private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

	// 添加处理文档内容的方法
	private Document processDocumentContent(Document document) {
		String content = document.getFormattedContent();
		int originalLength = content.length();

		String processedContent = truncateTextByTokens(content);

		if (!processedContent.equals(content)) {
			logger.info("Document truncated from {} to {} characters", originalLength, processedContent.length());
		}

		// 确保文档有ID
		Map<String, Object> metadata = new HashMap<>(document.getMetadata());
		if (!metadata.containsKey("id")) {
			if (metadata.containsKey("name")) {
				metadata.put("id", metadata.get("name"));
			} else {
				metadata.put("id", UUID.randomUUID().toString());
			}
		}

		return new Document(processedContent, metadata);
	}

	private String truncateTextByTokens(String text) {
		// 更保守的限制 - 使用更低的字符到token比率
		int maxChars = 1500; // 大约375 tokens (基于1:4比率)

		if (text.length() > maxChars) {
			String truncatedText = text.substring(0, maxChars);
			// 在句子边界截断
			int lastPeriod = truncatedText.lastIndexOf('.');
			int lastSpace = truncatedText.lastIndexOf(' ');

			// 优先在句号后截断，其次在空格处截断
			if (lastPeriod > 0) {
				truncatedText = truncatedText.substring(0, lastPeriod + 1);
			} else if (lastSpace > 0) {
				truncatedText = truncatedText.substring(0, lastSpace);
			}

			logger.debug("Truncated text from {} to {} characters", text.length(), truncatedText.length());
			return truncatedText;
		}
		return text;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {

		// 获取数据资源
		Resource file = data;

		if (!file.exists()) {
			logger.info("Local data file not found, downloading from URL...");
			file = downloadDataFile();
		}

		// 检查文档数量，如果已有足够数据则跳过加载
		if (checkDbCollectionRecords())
			return;

		// 检查文件是否有效
		if (file == null || !file.exists()) {
			logger.error("Data file is null or does not exist");
			return;
		}

		List<Document> documents = readDocumentsFromResource(file);

		if (documents == null || documents.isEmpty()) {
			logger.error("No ai documents found in the data file.");
			return;
		}
		vectorStore.add(documents);
		logger.info("Added {} documents to vector store", documents.size());

		// 构建 BM25 索引（当混合检索启用时）
		// 注意：如果Redis中已存在BM25索引数据，indexForBM25会跳过重新索引
		if (hybridSearchService != null) {
			logger.info("Building BM25 index for hybrid search...");
			hybridSearchService.indexForBM25(documents);
			logger.info("BM25 index built successfully with {} documents", documents.size());
		}

		logger.info("Embeddings created.");
	}

	/**
	 * 根据资源类型加载文档（不做向量化，不写 VectorStore）。
	 * 从 run() 中抽取，供启动加载与运维重建共用，保证分块/ID 与写入 Milvus 时一致。
	 */
	private List<Document> readDocumentsFromResource(Resource file) throws Exception {
		List<Document> documents = null;

		// 如果数据资源是.gz格式，则解压
		InputStreamResource inputStreamResource = null;
		if (file.getFilename() != null && file.getFilename().endsWith(".gz")) {
			logger.info("Decompressing GZIP file: {}", file.getFilename());
			GZIPInputStream inputStream = new GZIPInputStream(file.getInputStream());
			inputStreamResource = new InputStreamResource(inputStream, "beers.json");
		} else if (file.getFilename() != null && file.getFilename().endsWith(".zip")) {
			logger.info("Decompressing ZIP file: {}", file.getFilename());
			ZipInputStream zipinputStream = new ZipInputStream(file.getInputStream());
			zipinputStream.getNextEntry();
			inputStreamResource = new InputStreamResource(zipinputStream, "beers.json");
		} else if (file.getFilename() != null
				&& (file.getFilename().endsWith(".docx") || file.getFilename().endsWith(".pdf"))) {
			documents = hybridDocumentService.loadDocDirect(file);
			logger.info("Creating Embeddings...");

		} else {

			logger.info("Creating Embeddings...");

			// Create a JSON reader with fields relevant to our use case
			Resource resourceToUse = inputStreamResource != null ? inputStreamResource : file;
			logger.info("Using resource: {}", resourceToUse.getDescription());

			// 检查资源是否可读
			if (!resourceToUse.exists()) {
				logger.error("Resource does not exist: {}", resourceToUse.getDescription());
				return null;
			}

			// 直接使用 Resource 对象，而不是获取文件路径
			documents = hybridDocumentService.loadDocumentFromZip(resourceToUse);

			logger.info("Loaded {} documents from JSON", documents.size());

		}

		return documents;
	}

	/**
	 * 仅从原始数据源加载文档（不写 VectorStore、不做 embedding），供运维重建 BM25 使用。
	 */
	public List<Document> loadSourceDocuments() throws Exception {
		Resource file = data;
		if (!file.exists()) {
			logger.info("Local data file not found, downloading from URL...");
			file = downloadDataFile();
		}
		if (file == null || !file.exists()) {
			throw new IllegalStateException("Data source file is unavailable for BM25 rebuild");
		}
		return readDocumentsFromResource(file);
	}

	/**
	 * 运维重建 BM25 索引：先清空内存索引与 Redis 持久化，再从原始数据源重新构建并持久化。
	 * 用于修复“Milvus 已有数据但 BM25 索引缺失/不完整”的场景。
	 *
	 * @return 重建后 BM25 索引的文档数
	 */
	public int rebuildBm25FromSource() throws Exception {
		if (hybridSearchService == null) {
			throw new IllegalStateException(
					"Hybrid search is disabled (hybrid.search.enabled=false); cannot rebuild BM25 index");
		}
		logger.warn("Manual BM25 index rebuild requested. Clearing existing BM25 index and Redis persistence...");
		// 全量清空，避免 saveDocuments 因“existingCount>0”而拒绝覆盖、把不完整索引固化
		hybridSearchService.clearBM25Index();

		List<Document> documents = loadSourceDocuments();
		if (documents == null || documents.isEmpty()) {
			logger.error("BM25 rebuild aborted: no documents loaded from data source");
			return 0;
		}
		hybridSearchService.indexForBM25(documents);
		int count = hybridSearchService.getBm25DocumentCount();
		logger.info("BM25 index rebuilt with {} documents (source {} docs)", count, documents.size());
		return count;
	}

	private boolean checkDbCollectionRecords() {
		try {
			int numDocs = documentCountProvider.getDocumentCount(indexName);
			if (numDocs >= 10) {
				logger.info("Embeddings already loaded (found {} documents). Skipping", numDocs);
				return true;
			}
			logger.info("Found {} existing documents, proceeding with data loading", numDocs);
		} catch (UnsupportedOperationException e) {
			logger.warn("Document count check not supported for current VectorStore implementation: {}",
					e.getMessage());
			logger.info("Proceeding with data loading without document count check");
		}
		return false;
	}

	private Resource downloadDataFile() throws IOException {
		com.redis.demo.spring.ai.util.FileDownloader downloader = new com.redis.demo.spring.ai.util.FileDownloader();
		return downloader.downloadDataFile();
	}

}