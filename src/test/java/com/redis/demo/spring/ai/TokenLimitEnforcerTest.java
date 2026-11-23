package com.redis.demo.spring.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.redis.demo.spring.ai.util.MarkdownProcessor;
import com.redis.demo.spring.ai.util.ProcessingResult;
import com.redis.demo.spring.ai.util.TextChunk;
import com.redis.demo.spring.ai.util.TokenLimitEnforcer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * 覆盖 "Tokens in a single document exceeds the maximum number of allowed input tokens"
 * 的两条修复：分块大小有真实硬上限；入库前还有一道守卫。
 */
@DisplayName("Embedding token 上限守卫测试")
class TokenLimitEnforcerTest {

    private static String repeat(String unit, int times) {
        StringBuilder buffer = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            buffer.append(unit);
        }
        return buffer.toString();
    }

    @Test
    @DisplayName("守卫阈值应低于 TokenCountBatchingStrategy 的默认硬上限")
    void safeLimitIsBelowStrategyLimit() {
        assertThat(TokenLimitEnforcer.SAFE_TOKEN_LIMIT).isLessThan(TokenLimitEnforcer.STRATEGY_TOKEN_LIMIT);
    }

    @Test
    @DisplayName("没有句子边界的超长中文段落也能被硬切到阈值内")
    void hardSplitsOversizeDocumentWithoutDelimiters() {
        String text = repeat("向量检索的重排序模型可以显著提升召回质量这块内容用来复现分块超限的问题", 400);
        assertThat(TokenLimitEnforcer.countTokens(text)).isGreaterThan(TokenLimitEnforcer.STRATEGY_TOKEN_LIMIT);

        List<Document> guarded = TokenLimitEnforcer.enforceTokenLimit(List.of(new Document(text)));

        assertThat(guarded).hasSizeGreaterThan(1);
        assertThat(guarded).allSatisfy(document -> assertThat(
                TokenLimitEnforcer.countTokens(document.getText()))
                        .isLessThanOrEqualTo(TokenLimitEnforcer.STRATEGY_TOKEN_LIMIT));
        assertThat(guarded).allSatisfy(document -> assertThat(document.getMetadata()).containsKey("split_total"));
    }

    @Test
    @DisplayName("短文档原样通过守卫")
    void shortDocumentsAreUntouched() {
        List<Document> documents = List.of(new Document("短文本 A"), new Document("短文本 B"));

        assertThat(TokenLimitEnforcer.enforceTokenLimit(documents)).containsExactlyElementsOf(documents);
    }

    @Test
    @DisplayName("有句子边界时优先落在边界上而不是硬切")
    void prefersSentenceBoundaries() {
        String text = repeat("这是一个完整的句子。", 2000);

        List<String> pieces = TokenLimitEnforcer.splitToTokenLimit(text, 800, TokenLimitEnforcer.DEFAULT_DELIMITERS);

        assertThat(pieces).hasSizeGreaterThan(1);
        assertThat(pieces).allSatisfy(piece -> assertThat(piece).endsWith("。"));
    }

    @Test
    @DisplayName("MarkdownProcessor 每块都不超过 chunkTokenNum")
    void markdownChunksRespectTokenBudget() {
        String markdown = "# 实验结果\n\n" + repeat("本节报告模型在公开数据集上的表现。", 1200);

        ProcessingResult result = new MarkdownProcessor(800, TokenLimitEnforcer.DEFAULT_DELIMITERS, true)
                .processMarkdown(markdown);

        assertThat(result.getChunks()).hasSizeGreaterThan(1);
        assertThat(result.getChunks()).allSatisfy(chunk -> assertThat(
                TokenLimitEnforcer.countTokens(chunk.getContent())).isLessThanOrEqualTo(800));
    }

    @Test
    @DisplayName("超大表格按行拆块且每组都带上表头")
    void oversizeTableIsSplitByRows() {
        StringBuilder table = new StringBuilder("| 名称 | 描述 |\n|------|------|\n");
        for (int i = 0; i < 400; i++) {
            table.append("| 条目").append(i).append(" | ")
                    .append(repeat("这段描述足够长以模拟真实表格单元", 4)).append(" |\n");
        }
        String markdown = "# 参数表\n\n正文一段。\n\n" + table;

        ProcessingResult result = new MarkdownProcessor(800, TokenLimitEnforcer.DEFAULT_DELIMITERS, true)
                .processMarkdown(markdown);

        List<TextChunk> tableChunks = result.getChunks().stream()
                .filter(chunk -> "table".equals(chunk.getType()))
                .toList();
        assertThat(tableChunks).hasSizeGreaterThan(1);
        assertThat(tableChunks).allSatisfy(chunk -> {
            assertThat(TokenLimitEnforcer.countTokens(chunk.getContent())).isLessThanOrEqualTo(800);
            assertThat(chunk.getContent()).contains("<th>名称</th>");
        });
    }
}
