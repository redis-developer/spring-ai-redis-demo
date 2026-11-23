package com.redis.demo.spring.ai.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * Embedding 单条 token 上限守卫。
 *
 * <p>
 * {@code TokenCountBatchingStrategy} 在 {@code VectorStore.add} 之前会检查单个
 * Document 的 token 数，一旦超过它自己的阈值就直接抛
 * {@code IllegalArgumentException: Tokens in a single document exceeds the maximum
 * number of allowed input tokens}，整个数据加载（乃至应用启动）随之失败。
 * 该策略默认取 {@code 8191 * (1 - 0.1) = 7372} 个 CL100K_BASE token，且它用的是
 * jtokkit 真实分词，而不是「字符数 / 4」这类估算。
 *
 * <p>
 * 本类提供与守卫一致的 token 口径（同一个 {@link JTokkitTokenCountEstimator}，
 * 默认 CL100K_BASE，与 {@code TokenCountBatchingStrategy} 无参构造一致），并把超限
 * 文档在原边界（换行、句读）附近二次切分，保证任何上游解析器产出怪尺寸的分块都不会
 * 打断入库。
 */
public final class TokenLimitEnforcer {

	private static final Logger logger = LoggerFactory.getLogger(TokenLimitEnforcer.class);

	/** TokenCountBatchingStrategy 无参构造的实际阈值：8191 预留 10%。 */
	public static final int STRATEGY_TOKEN_LIMIT = (int) Math.round(8191 * 0.9);

	/**
	 * 二次切分目标。刻意低于 {@link #STRATEGY_TOKEN_LIMIT}：jtokkit 的 CL100K_BASE
	 * 与实际模型（如 bge-m3 的 8192 上下文）分词结果存在差异，留余量避免顶穿。
	 */
	public static final int SAFE_TOKEN_LIMIT = 6000;

	/** 与 {@code chunking.markdown.delimiter} 默认值一致的边界字符集。 */
	public static final String DEFAULT_DELIMITERS = "\n!?;。；！？";

	private static final TokenCountEstimator ESTIMATOR = new JTokkitTokenCountEstimator();

	private TokenLimitEnforcer() {
	}

	/** 与向量库批处理策略同口径的 token 计数。 */
	public static int countTokens(String text) {
		return (text == null || text.isEmpty()) ? 0 : ESTIMATOR.estimate(text);
	}

	public static boolean exceedsLimit(String text, int maxTokens) {
		return countTokens(text) > maxTokens;
	}

	/**
	 * 写入向量库前的兜底：把超过 {@link #SAFE_TOKEN_LIMIT} 的文档再切一遍。
	 * 未超限的文档原样保留，因此对 JSON 之类的短文本数据完全无副作用。
	 */
	public static List<Document> enforceTokenLimit(List<Document> documents) {
		return enforceTokenLimit(documents, SAFE_TOKEN_LIMIT);
	}

	public static List<Document> enforceTokenLimit(List<Document> documents, int maxTokens) {
		if (documents == null || documents.isEmpty()) {
			return documents;
		}
		List<Document> result = new ArrayList<>(documents.size());
		int oversizeCount = 0;
		for (Document document : documents) {
			String text = document.getText();
			int tokens = countTokens(text);
			if (tokens <= maxTokens) {
				result.add(document);
				continue;
			}
			List<String> pieces = splitToTokenLimit(text, maxTokens, DEFAULT_DELIMITERS);
			Map<String, Object> metadata = new HashMap<>(document.getMetadata());
			metadata.put("split_total", pieces.size());
			for (int i = 0; i < pieces.size(); i++) {
				Map<String, Object> pieceMetadata = new HashMap<>(metadata);
				pieceMetadata.put("split_part", i + 1);
				result.add(new Document(pieces.get(i), pieceMetadata));
			}
			oversizeCount++;
			logger.warn("Document {} 单条 token 数 {} 超过上限 {}，已二次切分为 {} 块",
					document.getId(), tokens, maxTokens, pieces.size());
		}
		if (oversizeCount > 0) {
			logger.info("token 上限守卫处理完成：{} 个超限文档被再切分，文档总数 {} -> {}",
					oversizeCount, documents.size(), result.size());
		}
		return result;
	}

	/**
	 * 按 token 上限切分文本：优先落在 {@code delimiters} 表示的句子/块边界上，
	 * 单个边界片段仍然超限时退回硬切分，确保返回的每一片都不超过 {@code maxTokens}。
	 */
	public static List<String> splitToTokenLimit(String text, int maxTokens, String delimiters) {
		List<String> chunks = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return chunks;
		}
		if (countTokens(text) <= maxTokens) {
			chunks.add(text);
			return chunks;
		}

		// 注意：delimiter 是「候选边界字符的集合」，必须放进字符类；
		// 早先用 Pattern.quote 把它当成一整串字面量，导致切分永不命中、分块尺寸失控。
		String[] parts = text.split(boundaryRegex(delimiters));

		StringBuilder buffer = new StringBuilder();
		int bufferTokens = 0;
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			int partTokens = countTokens(part);
			if (partTokens > maxTokens) {
				flush(chunks, buffer);
				buffer = new StringBuilder();
				bufferTokens = 0;
				chunks.addAll(hardCut(part, maxTokens));
				continue;
			}
			if (bufferTokens + partTokens > maxTokens) {
				flush(chunks, buffer);
				buffer = new StringBuilder();
				bufferTokens = 0;
			}
			buffer.append(part);
			bufferTokens += partTokens;
		}
		flush(chunks, buffer);
		return chunks;
	}

	/** 构造形如 {@code (?<=[\n!?;。；！？])} 的后顾断言，即在边界字符之后下刀。 */
	private static String boundaryRegex(String delimiters) {
		StringBuilder characterClass = new StringBuilder("(?<=[");
		for (char c : delimiters.toCharArray()) {
			switch (c) {
			case '\n' -> characterClass.append("\\n");
			case '\r' -> characterClass.append("\\r");
			case '\t' -> characterClass.append("\\t");
			case ']', '\\', '^', '-' -> characterClass.append('\\').append(c);
			default -> characterClass.append(c);
			}
		}
		return characterClass.append("])").toString();
	}

	private static void flush(List<String> chunks, StringBuilder buffer) {
		String piece = buffer.toString().trim();
		if (!piece.isEmpty()) {
			chunks.add(piece);
		}
	}

	/**
	 * 不看语义边界的硬切分：二分定位「 substring 仍不超 maxTokens」的最大右端点，
	 * 并优先在这一段的最后一个换行/空格处收尾，避免把词切断。
	 */
	private static List<String> hardCut(String text, int maxTokens) {
		List<String> pieces = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int end = findEndWithinLimit(text, start, maxTokens);
			int natural = lastNaturalBoundary(text, start, end);
			int cut = natural > start ? natural : end;
			if (cut > start + 1 && Character.isHighSurrogate(text.charAt(cut - 1))) {
				cut--;
			}
			pieces.add(text.substring(start, cut).trim());
			start = Math.max(cut, start + 1);
		}
		pieces.removeIf(String::isEmpty);
		return pieces;
	}

	private static int findEndWithinLimit(String text, int start, int maxTokens) {
		int low = start + 1;
		int high = text.length();
		int best = low;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			if (countTokens(text.substring(start, mid)) <= maxTokens) {
				best = mid;
				low = mid + 1;
			} else {
				high = mid - 1;
			}
		}
		return best;
	}

	/** 在后半段里回找自然边界；找不到就返回 -1，交由调用方硬切。 */
	private static int lastNaturalBoundary(String text, int start, int end) {
		for (int i = end - 1; i > start + (end - start) / 2; i--) {
			char c = text.charAt(i);
			if (c == '\n' || c == ' ' || c == '\t') {
				return i + 1;
			}
		}
		return -1;
	}
}
