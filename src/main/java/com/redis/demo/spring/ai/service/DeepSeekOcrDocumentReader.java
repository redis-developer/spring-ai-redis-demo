package com.redis.demo.spring.ai.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * DeepSeek-OCR Document Reader
 * 使用硅基流动 (SiliconFlow) 的 DeepSeek-OCR 多模态模型处理 PDF 文档
 * 通过 chat/completions API 将 PDF 页面图片转换为 Markdown 格式文本
 * 
 * 注意：由于 API 只支持 text 和 image_url 类型，需要先将 PDF 转换为图片
 * 
 * 优化特性：
 * - 并行处理多个页面
 * - JPEG 压缩减小图片体积
 * - HTTP 超时配置
 * 
 * API 文档:
 * https://docs.siliconflow.cn/cn/api-reference/chat-completions/chat-completions
 */
public class DeepSeekOcrDocumentReader {

    private static final Logger logger = LoggerFactory.getLogger(DeepSeekOcrDocumentReader.class);

    /**
     * 硅基流动 Chat Completions API 地址
     */
    private static final String SILICONFLOW_API_URL = "https://api.siliconflow.cn/v1/chat/completions";

    /**
     * DeepSeek-OCR 模型名称
     */
    private static final String DEFAULT_MODEL = "deepseek-ai/DeepSeek-OCR";

    /**
     * OCR 提示词，要求模型将图片转换为 Markdown
     */
    private static final String OCR_PROMPT = "Convert the document to markdown.";

    /**
     * PDF 渲染 DPI (降低以减小图片体积)
     */
    private static final int RENDER_DPI = 100;

    /**
     * JPEG 压缩质量 (0.0-1.0)
     */
    private static final float JPEG_QUALITY = 0.9f;

    /**
     * 并行处理的线程数
     */
    private static final int PARALLEL_THREADS = 4;

    /**
     * API 请求超时时间（秒）
     */
    private static final int REQUEST_TIMEOUT_SECONDS = 120;

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    
    /**
     * 保存OCR结果的目录
     */
    private String outputDirectory = "ocr-output";

    /**
     * 构造函数
     * 
     * @param apiKey 硅基流动 API Key (从环境变量 SILICONFLOW_KEY 获取)
     */
    public DeepSeekOcrDocumentReader(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    /**
     * 构造函数
     * 
     * @param apiKey 硅基流动 API Key
     * @param model  OCR 模型名称
     */
    public DeepSeekOcrDocumentReader(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = JsonMapper.builder().build();
        this.executorService = Executors.newFixedThreadPool(PARALLEL_THREADS);

        // 配置带超时的 RestTemplate
        this.restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("Siliconflow API Key is empty! Please set SILICONFLOW_KEY environment variable.");
        }
    }
    
    /**
     * 设置OCR结果保存目录
     * 
     * @param outputDirectory 保存目录路径
     */
    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
    
    /**
     * 获取OCR结果保存目录
     * 
     * @return 保存目录路径
     */
    public String getOutputDirectory() {
        return this.outputDirectory;
    }

    /**
     * 读取 PDF 文件并转换为 Document 列表
     * 
     * @param pdfResource PDF 资源
     * @return Document 列表，每个 Document 包含 Markdown 格式的文本内容
     * @throws IOException 读取文件失败
     */
    public List<Document> read(Resource pdfResource) throws IOException {
        if (pdfResource == null || !pdfResource.exists()) {
            logger.warn("PDF resource is null or does not exist");
            return new ArrayList<>();
        }

        String filename = pdfResource.getFilename();
        logger.info("Starting DeepSeek-OCR processing for file: {}", filename);

        try {
            // 1. 将 PDF 每页转换为图片的 Base64 编码
            List<String> pageImages = convertPdfToImages(pdfResource);
            logger.info("PDF converted to {} page images (DPI={}, JPEG quality={})",
                    pageImages.size(), RENDER_DPI, JPEG_QUALITY);

            // 2. 并行调用 DeepSeek-OCR API 处理所有页面
            String markdownContent = processPagesConcurrently(pageImages);
            logger.info("DeepSeek-OCR completed, total {} characters", markdownContent.length());

            // 3. 将 Markdown 内容转换为 Document 列表
            List<Document> documents = parseMarkdownToDocuments(markdownContent, filename);
            logger.info("Created {} document(s) from OCR result", documents.size());

            return documents;

        } catch (Exception e) {
            logger.error("DeepSeek-OCR processing failed for file {}: {}", filename, e.getMessage(), e);
            throw new IOException("Failed to process PDF with DeepSeek-OCR: " + e.getMessage(), e);
        }
    }

    /**
     * 并行处理多个页面
     */
    private String processPagesConcurrently(List<String> pageImages) {
        logger.info("Processing {} pages with {} parallel threads", pageImages.size(), PARALLEL_THREADS);

        // 创建所有页面的异步任务
        List<CompletableFuture<PageResult>> futures = new ArrayList<>();
        for (int i = 0; i < pageImages.size(); i++) {
            final int pageIndex = i;
            final String pageImage = pageImages.get(i);

            CompletableFuture<PageResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    logger.info("Processing page {}/{} (size: {} KB)",
                            pageIndex + 1, pageImages.size(), pageImage.length() / 1024);
                    String markdown = callDeepSeekOcrApi(pageImage);
                    logger.info("Page {}/{} completed", pageIndex + 1, pageImages.size());
                    return new PageResult(pageIndex, markdown, null);
                } catch (Exception e) {
                    logger.error("Page {} failed: {}", pageIndex + 1, e.getMessage());
                    return new PageResult(pageIndex, "", e);
                }
            }, executorService);

            futures.add(future);
        }

        // 等待所有任务完成并按页码顺序合并结果
        List<PageResult> results = futures.stream()
                .map(CompletableFuture::join)
                .sorted(Comparator.comparingInt(r -> r.pageIndex))
                .collect(Collectors.toList());

        // 合并所有页面的 Markdown
        StringBuilder allMarkdown = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            PageResult result = results.get(i);
            if (result.error != null) {
                allMarkdown.append("\n\n[Page ").append(result.pageIndex + 1).append(" OCR failed]\n\n");
            } else {
                allMarkdown.append(result.markdown);
            }
            if (i < results.size() - 1) {
                allMarkdown.append("\n\n---\n\n");
            }
        }

        logger.info("Merged {} page(s) into a single Markdown document", results.size());
        return allMarkdown.toString();
    }

    /**
     * 将 PDF 转换为每页的 Base64 编码 JPEG 图片列表
     */
    private List<String> convertPdfToImages(Resource pdfResource) throws IOException {
        List<String> base64Images = new ArrayList<>();

        try (InputStream inputStream = pdfResource.getInputStream();
                PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int page = 0; page < pageCount; page++) {
                // 渲染页面为图片
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, RENDER_DPI, ImageType.RGB);

                // 使用 JPEG 压缩减小体积
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                writeJpegWithQuality(image, baos, JPEG_QUALITY);
                byte[] imageBytes = baos.toByteArray();
                String base64 = Base64.getEncoder().encodeToString(imageBytes);

                base64Images.add(base64);
                logger.debug("Page {} converted, size: {} KB", page + 1, imageBytes.length / 1024);
            }
        }

        return base64Images;
    }

    /**
     * 使用指定质量写入 JPEG 图片
     */
    private void writeJpegWithQuality(BufferedImage image, ByteArrayOutputStream baos, float quality)
            throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /**
     * 调用硅基流动 DeepSeek-OCR API (使用 image_url 格式)
     * 
     * @param base64Image 页面图片的 Base64 编码
     * @return Markdown 格式的文档内容
     */
    private String callDeepSeekOcrApi(String base64Image) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // 构造请求体 - 使用多模态消息格式 (image_url 类型)
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4096);

        // 构造 messages 数组
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        // 构造多模态 content 数组 (使用 image_url 格式)
        List<Map<String, Object>> contentArray = new ArrayList<>();

        // 添加图片内容 (使用 image_url 类型)
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", "data:image/jpeg;base64," + base64Image);
        imageContent.put("image_url", imageUrl);
        contentArray.add(imageContent);

        // 添加文本提示
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", OCR_PROMPT);
        contentArray.add(textContent);

        userMessage.put("content", contentArray);
        messages.add(userMessage);
        requestBody.put("messages", messages);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(SILICONFLOW_API_URL, entity, String.class);

        if (response.getBody() == null) {
            throw new RuntimeException("Empty response from DeepSeek-OCR API");
        }

        // 解析响应，提取 Markdown 内容
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
        JsonNode choicesNode = jsonNode.get("choices");

        if (choicesNode == null || !choicesNode.isArray() || choicesNode.isEmpty()) {
            throw new RuntimeException("Invalid response format: missing 'choices' array");
        }

        JsonNode messageNode = choicesNode.get(0).get("message");
        if (messageNode == null) {
            throw new RuntimeException("Invalid response format: missing 'message' in choices");
        }

        JsonNode contentNode = messageNode.get("content");
        if (contentNode == null) {
            throw new RuntimeException("Invalid response format: missing 'content' in message");
        }

        return contentNode.asText();
    }

    /**
     * 将 Markdown 内容转换为 Document 列表
     * 返回单个 Document，包含整个 Markdown 内容
     * 后续由 TokenTextSplitter 进行分块处理
     */
    private List<Document> parseMarkdownToDocuments(String markdownContent, String filename) {
        List<Document> documents = new ArrayList<>();

        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            logger.warn("Empty markdown content from OCR");
            return documents;
        }

        // 保存 Markdown 内容到本地文件
        saveMarkdownToFile(markdownContent, filename);

        // 创建单个 Document，包含完整的 Markdown 内容
        // 后续由 HybridDocumentService 中的 TokenTextSplitter 进行分块
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", filename);
        metadata.put("type", "pdf-ocr");
        metadata.put("ocr_model", model);

        Document document = new Document(markdownContent.trim(), metadata);
        documents.add(document);

        return documents;
    }
    
    /**
     * 将 Markdown 内容保存到本地文件
     * 
     * @param markdownContent Markdown 内容
     * @param originalFilename 原始文件名
     */
    private void saveMarkdownToFile(String markdownContent, String originalFilename) {
        try {
            // 创建输出目录（如果不存在）
            Path outputPath = Paths.get(outputDirectory);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            
            // 生成 Markdown 文件名（替换原始文件的扩展名为 .md）
            String baseFilename = originalFilename.replaceAll("\\.[^\\.]*$", "");
            String markdownFilename = baseFilename + ".md";
            Path markdownFilePath = outputPath.resolve(markdownFilename);
            
            // 写入 Markdown 文件
            Files.write(markdownFilePath, markdownContent.getBytes("UTF-8"));
            
            logger.info("Markdown content saved to: {}", markdownFilePath.toString());
            
        } catch (IOException e) {
            logger.error("Failed to save markdown content to file: {}", e.getMessage(), e);
        }
    }

    /**
     * 页面处理结果
     */
    private static class PageResult {
        final int pageIndex;
        final String markdown;
        final Exception error;

        PageResult(int pageIndex, String markdown, Exception error) {
            this.pageIndex = pageIndex;
            this.markdown = markdown;
            this.error = error;
        }
    }
}
