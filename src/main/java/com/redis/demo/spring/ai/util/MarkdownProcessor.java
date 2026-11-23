package com.redis.demo.spring.ai.util;

import java.util.*;
import java.util.regex.*;

/**
 * Markdown处理工具类
 * 用于解析Markdown文本，提取表格，并进行分块处理
 */
public class MarkdownProcessor {

    // 默认配置常量
    private static final int DEFAULT_CHUNK_TOKEN_NUM = 128;
    private static final String DEFAULT_DELIMITER = "\n!?;。；！？";
    private static final String CHUNK_TYPE_TABLE = "table";

    // 正则表达式模式常量
    // 标准Markdown表格模式
    private static final String BORDER_TABLE_REGEX = "(?:\\n|^)(?:\\|.*?\\|.*?\\|.*?\\n)(?:\\|(?:\\s*[:-]+[-| :]*\\s*)\\|.*?\\n)(?:\\|.*?\\|.*?\\|.*?\\n)+";
    // 无边框表格模式
    private static final String NO_BORDER_TABLE_REGEX = "(?:\\n|^)(?:\\S.*?\\|.*?\\n)(?:(?:\\s*[:-]+[-| :]*\\s*).*?\\n)(?:\\S.*?\\|.*?\\n)+";
    // HTML表格模式
    private static final String HTML_TABLE_REGEX = "(?:\\n|^)\\s*(?:(?:<html[^>]*>\\s*<body[^>]*>\\s*<table[^>]*>.*?</table>\\s*</body>\\s*</html>)|(?:<body[^>]*>\\s*<table[^>]*>.*?</table>\\s*</body>)|(?:<table[^>]*>.*?</table>))\\s*(?=\\n|$)";
    // 表格分隔行模式 (|---|---|)
    private static final String TABLE_SEPARATOR_REGEX = "^\\|\\s*[:\\-\\|\\s]*\\|$";
    // HTML标签清理模式
    private static final Pattern HTML_TAG_CLEANER_PATTERN = Pattern.compile("<(/?)(\\w+)[^>]*>",
            Pattern.CASE_INSENSITIVE);

    // 编译好的Pattern对象
    private static final Pattern BORDER_TABLE_PATTERN = Pattern.compile(BORDER_TABLE_REGEX, Pattern.MULTILINE);
    private static final Pattern NO_BORDER_TABLE_PATTERN = Pattern.compile(NO_BORDER_TABLE_REGEX, Pattern.MULTILINE);
    private static final Pattern HTML_TABLE_PATTERN = Pattern.compile(HTML_TABLE_REGEX,
            Pattern.MULTILINE | Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // 实例配置参数
    // chunkTokenNum 现在按真实 token（jtokkit CL100K_BASE）计，必须显著小于
    // TokenLimitEnforcer.STRATEGY_TOKEN_LIMIT，否则单个分块会顶穿 embedding 批处理阈值
    private int chunkTokenNum = DEFAULT_CHUNK_TOKEN_NUM;
    private String delimiter = DEFAULT_DELIMITER;
    private boolean separateTables = true;

    public MarkdownProcessor(int chunkTokenNum, String delimiter, boolean separateTables) {
        this.chunkTokenNum = chunkTokenNum;
        this.delimiter = delimiter;
        this.separateTables = separateTables;
    }

    /**
     * 主处理方法：处理Markdown文本，提取表格并分块
     *
     * @param markdownText 输入的Markdown文本
     * @return 处理结果，包含分块列表和提取的表格
     */
    public ProcessingResult processMarkdown(String markdownText) {
        // 1. 表格提取与分离
        // 先将表格从文本中提取出来，避免干扰后续的文本分块
        TableExtractionResult tableResult = extractTablesAndRemainder(markdownText);

        // 2. 元素提取
        // 对剩余的非表格文本进行元素提取（如段落、列表等）
        MarkdownElementExtractor extractor = new MarkdownElementExtractor(tableResult.getRemainder());
        List<MarkdownElement> elements = extractor.extractElements(delimiter, true);

        // 3. 分块处理
        // 将提取的元素和表格合并并按照token限制进行分块
        List<TextChunk> chunks = processChunks(elements, tableResult.getTables());

        return new ProcessingResult(chunks, tableResult.getTables());
    }

    /**
     * 提取表格并返回剩余文本
     *
     * @param markdownText 输入文本
     * @return 包含处理后文本和表格列表的结果对象
     */
    private TableExtractionResult extractTablesAndRemainder(String markdownText) {
        List<String> tables = new ArrayList<>();
        String workingText = markdownText;

        // 依次提取不同类型的表格
        // 1. 提取标准Markdown表格
        workingText = extractTables(workingText, BORDER_TABLE_PATTERN, tables);
        // 2. 提取无边框表格
        workingText = extractTables(workingText, NO_BORDER_TABLE_PATTERN, tables);
        // 3. 提取HTML表格
        workingText = extractTables(workingText, HTML_TABLE_PATTERN, tables);

        return new TableExtractionResult(workingText, tables);
    }

    /**
     * 使用指定模式提取表格
     *
     * @param text    输入文本
     * @param pattern 匹配模式
     * @param tables  用于存储提取到的表格的列表
     * @return 移除表格后的文本
     */
    private String extractTables(String text, Pattern pattern, List<String> tables) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String table = matcher.group();
            tables.add(table);

            if (separateTables) {
                // 如果配置为分离表格，则在原文中替换为空行
                matcher.appendReplacement(result, "\n\n");
            } else {
                // 如果不分离，保留表格但转换为HTML格式以便统一处理
                String htmlTable = convertTableToHtml(table);
                matcher.appendReplacement(result, Matcher.quoteReplacement(htmlTable + "\n\n"));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 处理分块逻辑
     *
     * @param elements 提取的文本元素
     * @param tables   提取的表格
     * @return 最终的文本块列表
     */
    private List<TextChunk> processChunks(List<MarkdownElement> elements, List<String> tables) {
        List<TextChunk> chunks = new ArrayList<>();

        // 处理文本元素分块
        for (MarkdownElement element : elements) {
            String content = element.getContent();

            // 计算 token 数量（jtokkit 真实分词）
            int tokenCount = estimateTokenCount(content);

            if (tokenCount <= chunkTokenNum) {
                // 如果元素大小在限制范围内，直接作为一个块
                TextChunk chunk = new TextChunk(
                        content,
                        element.getType(),
                        element.getStartLine(),
                        element.getEndLine());
                chunks.add(chunk);
            } else {
                // 如果元素过大，需要进一步按分隔符分割
                List<TextChunk> subChunks = splitContent(content, element);
                chunks.addAll(subChunks);
            }
        }

        // 处理表格：整张表可能远超 chunkTokenNum（HTML 标签还会放大长度），必须按行拆块
        for (String table : tables) {
            String htmlTable = convertTableToHtml(table);
            if (estimateTokenCount(htmlTable) <= chunkTokenNum) {
                chunks.add(new TextChunk(htmlTable, CHUNK_TYPE_TABLE, -1, -1));
            } else {
                chunks.addAll(splitOversizeTable(table, htmlTable));
            }
        }

        return chunks;
    }

    /**
     * 将Markdown表格转换为HTML格式
     *
     * @param markdownTable Markdown格式的表格字符串
     * @return HTML格式的表格字符串
     */
    public String convertTableToHtml(String markdownTable) {
        if (markdownTable == null || markdownTable.trim().isEmpty()) {
            return "";
        }

        // 检查是否已经是HTML表格，如果是则进行清理
        if (markdownTable.trim().toLowerCase().contains("<table>")) {
            return cleanHtmlTable(markdownTable);
        }

        // 解析Markdown表格内容
        List<String[]> tableData = parseMarkdownTable(markdownTable);
        return buildHtmlTable(tableData);
    }

    /**
     * 将二维表格数据渲染为HTML，第一行按表头处理
     *
     * @param tableData 表格数据
     * @return HTML表格字符串
     */
    private String buildHtmlTable(List<String[]> tableData) {
        if (tableData == null || tableData.isEmpty()) {
            return "";
        }
        return buildHtmlTable(tableData.get(0), tableData.subList(1, tableData.size()));
    }

    /**
     * 用指定表头 + 数据行渲染HTML表格，便于大表按行拆块时每组都带上表头
     */
    private String buildHtmlTable(String[] header, List<String[]> dataRows) {
        StringBuilder html = new StringBuilder();
        html.append("<table>\n");
        html.append(buildHtmlRow(header, true));
        for (String[] row : dataRows) {
            html.append(buildHtmlRow(row, false));
        }
        html.append("</table>");
        return html.toString();
    }

    private String buildHtmlRow(String[] row, boolean isHeader) {
        StringBuilder html = new StringBuilder("  <tr>\n");
        for (String cell : row) {
            String tag = isHeader ? "th" : "td";
            html.append("    <").append(tag).append(">")
                    .append(escapeHtml(cell.trim()))
                    .append("</").append(tag).append(">\n");
        }
        return html.append("  </tr>\n").toString();
    }

    /**
     * 把超大的表格按数据行拆成多个表格块，每块重复表头；
     * 单行本身仍超限时退回通用 token 硬切分，保证不会有整张表原样进 embedding
     */
    private List<TextChunk> splitOversizeTable(String markdownTable, String htmlTable) {
        List<String[]> tableData = parseMarkdownTable(markdownTable);
        if (tableData.size() < 2) {
            return hardSplitChunks(htmlTable, CHUNK_TYPE_TABLE);
        }

        String[] header = tableData.get(0);
        List<TextChunk> chunks = new ArrayList<>();
        List<String[]> group = new ArrayList<>();
        for (int i = 1; i < tableData.size(); i++) {
            group.add(tableData.get(i));
            if (estimateTokenCount(buildHtmlTable(header, group)) <= chunkTokenNum) {
                continue;
            }
            if (group.size() == 1) {
                chunks.addAll(hardSplitChunks(buildHtmlTable(header, group), CHUNK_TYPE_TABLE));
                group = new ArrayList<>();
                continue;
            }
            String[] overflow = group.remove(group.size() - 1);
            chunks.add(new TextChunk(buildHtmlTable(header, group), CHUNK_TYPE_TABLE, -1, -1));
            group = new ArrayList<>();
            group.add(overflow);
        }
        if (!group.isEmpty()) {
            chunks.add(new TextChunk(buildHtmlTable(header, group), CHUNK_TYPE_TABLE, -1, -1));
        }
        return chunks;
    }

    /**
     * 按 chunkTokenNum 对任意内容做兜底切分，产出的块统一带上指定类型
     */
    private List<TextChunk> hardSplitChunks(String content, String type) {
        List<TextChunk> chunks = new ArrayList<>();
        for (String piece : TokenLimitEnforcer.splitToTokenLimit(content, chunkTokenNum, delimiter)) {
            chunks.add(new TextChunk(piece, type, -1, -1));
        }
        return chunks;
    }

    /**
     * 解析Markdown表格为二维数组
     * 
     * @param markdownTable Markdown表格字符串
     * @return 表格数据的二维列表
     */
    private List<String[]> parseMarkdownTable(String markdownTable) {
        List<String[]> tableData = new ArrayList<>();
        String[] lines = markdownTable.trim().split("\n");

        for (String line : lines) {
            line = line.trim();

            // 跳过Markdown表格的分隔行 (如 |---|---|)
            if (line.matches(TABLE_SEPARATOR_REGEX)) {
                continue;
            }

            // 处理有效的表格行
            if (line.startsWith("|") && line.endsWith("|")) {
                // 移除首尾的|，然后按|分割单元格
                String content = line.substring(1, line.length() - 1);
                String[] cells = content.split("\\|");

                // 清理每个单元格的内容
                for (int i = 0; i < cells.length; i++) {
                    cells[i] = cells[i].trim();
                }

                tableData.add(cells);
            }
        }

        return tableData;
    }

    /**
     * 清理HTML表格标签，移除所有属性，只保留标签名
     *
     * @param htmlTable 原始HTML表格
     * @return 清理后的HTML表格
     */
    private String cleanHtmlTable(String htmlTable) {
        Matcher matcher = HTML_TAG_CLEANER_PATTERN.matcher(htmlTable);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String closingSlash = matcher.group(1);
            String tagName = matcher.group(2);
            // 替换为不带属性的纯标签
            matcher.appendReplacement(result, "<" + closingSlash + tagName + ">");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * HTML特殊字符转义
     *
     * @param text 原始文本
     * @return 转义后的文本
     */
    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 将过长的内容进行分割。
     * 切分优先落在句子/块边界上，并且保证每个块都不超过 chunkTokenNum；
     * 旧实现把 delimiter 整串 Pattern.quote 成字面量，导致切分永不命中、整段原样成块。
     *
     * @param content         内容文本
     * @param originalElement 原始元素信息，用于保留元数据
     * @return 分割后的文本块列表
     */
    private List<TextChunk> splitContent(String content, MarkdownElement originalElement) {
        List<TextChunk> chunks = new ArrayList<>();
        for (String piece : TokenLimitEnforcer.splitToTokenLimit(content, chunkTokenNum, delimiter)) {
            chunks.add(new TextChunk(
                    piece,
                    originalElement.getType(),
                    originalElement.getStartLine(),
                    originalElement.getEndLine()));
        }
        return chunks;
    }

    /**
     * 估算文本的Token数量。
     * 与入库守卫、TokenCountBatchingStrategy 同口径（jtokkit CL100K_BASE 真实分词）；
     * 旧实现用 length()/4，对中文、公式和HTML表格严重低估，是超限报错的根因之一。
     *
     * @param text 输入文本
     * @return 估算的Token数
     */
    private int estimateTokenCount(String text) {
        return TokenLimitEnforcer.countTokens(text);
    }
}