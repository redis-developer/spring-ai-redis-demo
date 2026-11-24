package com.redis.demo.spring.ai.util;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Ollama HTTP 客户端工厂。
 *
 * <p>
 * Spring Boot 4.2 / Spring Framework 7.1 起，{@code RestClient.builder()} 的默认请求工厂从
 * {@code JdkClientHttpRequestFactory}（不限读超时）换成了
 * {@code ReactorClientHttpRequestFactory}；后者即使自身的 {@code readTimeout} 字段为
 * {@code null}，实测请求也会在约 10 秒被 {@code io.netty.handler.timeout.ReadTimeoutException}
 * 打断（迁移前同一探针跑满 90 秒才返回）。
 *
 * <p>
 * {@code OllamaApi.builder()} 内部用的正是静态的 {@code RestClient.builder()}，因此容器里的
 * {@code RestClientCustomizer} 对它完全不生效，Ollama 链路的超时一直由这个隐式默认值决定。
 * 本地 bge-m3 实测 40 段文本的 {@code /api/embed} 需要约 47 秒，批量向量化必然被这个 10 秒打断。
 *
 * <p>
 * 本类显式指定 JDK HttpClient 工厂与连接/读取超时，把超时重新交回本项目掌控。
 */
public final class OllamaRestClientFactory {

	private OllamaRestClientFactory() {
	}

	/**
	 * 构造带显式超时的 {@link RestClient.Builder}，交给
	 * {@code OllamaApi.builder().restClientBuilder(...)} 使用。
	 *
	 * @param connectTimeout 连接超时
	 * @param readTimeout    读取超时，需大于单个 embedding / chat 请求的最坏耗时
	 */
	public static RestClient.Builder restClientBuilder(Duration connectTimeout, Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().requestFactory(requestFactory);
	}
}
