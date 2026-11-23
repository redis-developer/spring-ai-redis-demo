package com.redis.demo.spring.ai.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.redis.demo.spring.ai.RagDataLoader;
import com.redis.demo.spring.ai.parse.RAGFlowDocxParser;
import com.redis.demo.spring.ai.util.MarkdownProcessor;
import com.redis.demo.spring.ai.util.ProcessingResult;
import com.redis.demo.spring.ai.util.TextChunk;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class HybridDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(HybridDocumentService.class);

    private final RAGFlowDocxParser customDocxParser = new RAGFlowDocxParser();

    // DeepSeek-OCR 文档读取器
    private final DeepSeekOcrDocumentReader deepSeekOcrReader;

    // 学术论文分块配置参数 (TokenTextSplitter)
    @Value("${chunking.defaultChunkSize:800}")
    private int defaultChunkSize;

    @Value("${chunking.minChunkSizeChars:350}")
    private int minChunkSizeChars;

    @Value("${chunking.minChunkLengthToEmbed:5}")
    private int minChunkLengthToEmbed;

    @Value("${chunking.maxNumChunks:10000}")
    private int maxNumChunks;

    @Value("${chunking.keepSeparator:true}")
    private boolean keepSeparator;

    // 切块算法配置：markdown（自定义Markdown切块，默认）或 token（TokenTextSplitter）
    @Value("${chunking.algorithm:markdown}")
    private String chunkingAlgorithm;

    // MarkdownProcessor 自定义切块参数
    @Value("${chunking.markdown.chunkTokenNum:128}")
    private int markdownChunkTokenNum;

    @Value("${chunking.markdown.delimiter:\n!?;。；！？}")
    private String markdownDelimiter;

    @Value("${chunking.markdown.separateTables:true}")
    private boolean markdownSeparateTables;

    /**
     * 构造函数，初始化 DeepSeek-OCR 读取器
     * 
     * @param siliconflowApiKey 硅基流动 API Key (从环境变量或配置获取)
     * @param ocrModel          OCR 模型名称
     */
    public HybridDocumentService(
            @Value("${SILICONFLOW_KEY:#{environment.SILICONFLOW_KEY}}") String siliconflowApiKey,
            @Value("${deepseek-ocr.model:deepseek-ai/DeepSeek-OCR}") String ocrModel) {
        // 尝试从环境变量获取 API Key
        String apiKey = siliconflowApiKey;
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("environment.SILICONFLOW_KEY")) {
            apiKey = System.getenv("SILICONFLOW_KEY");
        }
        this.deepSeekOcrReader = new DeepSeekOcrDocumentReader(apiKey, ocrModel);
        logger.info("HybridDocumentService initialized with DeepSeek-OCR model: {}", ocrModel);
    }

    public List<Document> loadDocDirect(Resource resource) throws IOException, InvalidFormatException {
        String filename = resource.getFilename();
        if (filename.endsWith(".docx")) {
            // 由于 RAGFlowDocxParser 只接受文件路径，我们暂时仍需要获取文件路径
            // 但我们可以检查是否可以访问文件
            if (resource.isFile()) {
                RAGFlowDocxParser.ParseResult result = customDocxParser.parse(resource.getFile().getAbsolutePath(),
                        null);

                // 2. 转换 Paragraphs
                List<Document> docs = customDocxParser.convertSectionsToDocuments(result.getSections());

                // 3. 转换 Tables (你的代码把表格作为 List<List<String>> 返回，你需要决定如何把它们变成 Document)
                for (List<String> tableLines : result.getTables()) {
                    String tableText = String.join("\n", tableLines);
                    docs.add(new Document(tableText, Map.of("type", "table")));
                }
                return docs;
            } else {
                throw new IOException("DOCX files must be accessed as files, not as streams");
            }
        } else if (filename.endsWith(".pdf")) {
            // 使用 DeepSeek-OCR 多模态 API 处理 PDF 文档
            // 通过硅基流动 API 将 PDF 转换为 Markdown 格式
            logger.info("开始使用 DeepSeek-OCR 处理 PDF 文件: {}", filename);

            List<Document> documents = deepSeekOcrReader.read(resource);

            logger.info("DeepSeek-OCR 处理完成: 获取到 {} 个文档", documents.size());

            List<Document> splitDocs;

            // 根据配置选择切块算法
            if ("markdown".equalsIgnoreCase(chunkingAlgorithm)) {
                // 使用自定义 MarkdownProcessor 分块
                // 基于 Markdown 语义结构（表格、段落等）进行分块
                logger.info("使用 MarkdownProcessor 切块算法, chunkTokenNum={}, delimiter长度={}, separateTables={}",
                        markdownChunkTokenNum, markdownDelimiter.length(), markdownSeparateTables);

                splitDocs = new java.util.ArrayList<>();
                for (Document doc : documents) {
                    String markdownContent = doc.getText();
                    MarkdownProcessor processor = new MarkdownProcessor(
                            markdownChunkTokenNum,
                            markdownDelimiter,
                            markdownSeparateTables);
                    ProcessingResult result = processor.processMarkdown(markdownContent);

                    // 将 TextChunk 转换为 Document
                    for (TextChunk chunk : result.getChunks()) {
                        Map<String, Object> metadata = new java.util.HashMap<>(doc.getMetadata());
                        metadata.put("chunk_type", chunk.getType());
                        metadata.put("start_line", chunk.getStartLine());
                        metadata.put("end_line", chunk.getEndLine());
                        splitDocs.add(new Document(chunk.getContent(), metadata));
                    }
                }

                logger.info("MarkdownProcessor 分块完成: 原始{}个文档 -> 分块后{}个文档", documents.size(), splitDocs.size());
            } else {
                TokenTextSplitter splitter = getTokenTextSplitter();

                logger.info(
                        "使用 TokenTextSplitter 切块算法: chunkSize={}, minChunkSizeChars={}, minChunkLengthToEmbed={}, maxNumChunks={}, keepSeparator={}",
                        defaultChunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator);

                splitDocs = splitter.split(documents);
                logger.info("TokenTextSplitter 分块完成: 原始{}个文档 -> 分块后{}个文档", documents.size(), splitDocs.size());
            }

            return splitDocs;
        }
        return new JsonReader(resource, RagDataLoader.KEYS).get();
    }

    private @NotNull TokenTextSplitter getTokenTextSplitter() {
        List<Character> characterList =  List.of('.', '。', '!', '？', ';', '\n');
        // 使用 TokenTextSplitter 分块（token 算法）
        // 学术论文优化的分块逻辑
        // - defaultChunkSize: 800 tokens，适合论文段落的完整性
        // - minChunkSizeChars: 350 字符，避免过小的分块
        // - minChunkLengthToEmbed: 5，过滤过短的无意义文本
        // - maxNumChunks: 10000，支持长论文
        // - keepSeparator: true，保留段落分隔符维持结构
        // - characterList 断句边界字符表"——文本分块时，切分器优先在这些标点符号后面下刀，让每个 chunk 的结尾尽量落在完整句子上，而不是从句子中间硬切
        TokenTextSplitter splitter = new TokenTextSplitter(
                defaultChunkSize,
                minChunkSizeChars,
                minChunkLengthToEmbed,
                maxNumChunks,
                keepSeparator,
                characterList);
        return splitter;
    }

    public List<Document> loadDocumentFromZip(Resource resource) throws IOException {
        return new JsonReader(resource, RagDataLoader.KEYS).get();
    }
}