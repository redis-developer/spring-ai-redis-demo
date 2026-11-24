package com.redis.demo.spring.ai.config;

import com.redis.demo.spring.ai.util.OllamaRestClientFactory;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * ChatModel 动态配置类
 * 根据 chat.provider 配置属性动态选择 Ollama 或 OpenAI 作为 ChatModel 提供者
 * 
 * 使用方式：
 * - chat.provider=ollama (默认) - 使用本地 Ollama 服务
 * - chat.provider=openai - 使用 OpenAI 兼容的 API（支持官方 OpenAI、硅基流动等）
 */
@Configuration
public class ChatModelConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ChatModelConfiguration.class);

    /**
     * OllamaApi Bean - 用于创建 Ollama ChatModel
     *
     * 同 RagConfiguration 里的 embedding：OllamaApi 不走容器的 RestClient.Builder，
     * Boot 4.2 后默认工厂变为 Reactor Netty 并附带约 10s 读超时，必须显式接管，
     * 否则 chat.provider=ollama 时稍长的生成会被静默打断。
     * 读超时沿用 spring.ai.ollama.read-timeout(60s)，保持 60s &lt; 65s 业务 &lt; 90s 异步的层级。
     */
    @Bean
    @ConditionalOnProperty(name = "chat.provider", havingValue = "ollama", matchIfMissing = true)
    public OllamaApi ollamaApi(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${spring.ai.ollama.connection-timeout:60s}") Duration connectionTimeout,
            @Value("${spring.ai.ollama.read-timeout:60s}") Duration readTimeout) {
        logger.info("Creating OllamaApi with baseUrl: {}, connectTimeout: {}, readTimeout: {}",
                baseUrl, connectionTimeout, readTimeout);
        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(OllamaRestClientFactory.restClientBuilder(connectionTimeout, readTimeout))
                .build();
    }

    /**
     * Ollama ChatModel - 当 chat.provider=ollama 时创建（默认）
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "chat.provider", havingValue = "ollama", matchIfMissing = true)
    public ChatModel ollamaChatModel(
            OllamaApi ollamaApi,
            ObjectProvider<ObservationRegistry> observationRegistry,   // ← 注入容器里的 registry
            @Value("${spring.ai.ollama.chat.options.model}") String model,
            @Value("${spring.ai.ollama.connection-timeout:60s}") Duration connectionTimeout,
            @Value("${spring.ai.ollama.read-timeout:60s}") Duration readTimeout) {
        logger.info("Initializing OllamaChatModel with model: {}", model);
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder().model(model).build())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))  // ← 关键
                .modelManagementOptions(ModelManagementOptions.builder()
                        .timeout(readTimeout)
                        .build())
                .build();
    }

    /**
     * OpenAI ChatModel - 当 chat.provider=openai 时创建
     * 支持官方 OpenAI API 以及兼容 API（如硅基流动等）
     *
     * Spring AI 2.1.0 移除了 OpenAiApi，OpenAI 集成改基于官方 SDK：
     * apiKey/baseUrl/timeout/model/temperature 全部下沉到 OpenAiChatOptions，
     * OpenAiChatModel.Builder 在 build() 时会依据 options 内部创建 OpenAIClient。
     * 由于新 SDK 不再走 Spring RestClient，原来的 RestClientCustomizer 对 OpenAI 超时不再生效；
     * 这里通过 OpenAiChatOptions.timeout 显式保留上游读取超时保护（60s &lt; 65s 业务 &lt; 90s 异步）。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "chat.provider", havingValue = "openai")
    public ChatModel openAiChatModel(
            ObjectProvider<ObservationRegistry> observationRegistry,   // ← 注入容器里的 registry
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${SILICONFLOW_KEY:${spring.ai.openai.api-key:${OPENAI_API_KEY:}}}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
            @Value("${spring.ai.openai.chat.options.temperature:0.7}") Double temperature,
            @Value("${spring.ai.openai.timeout:60s}") Duration timeout) {
        logger.info("Initializing OpenAiChatModel with baseUrl: {}, model: {}, temperature: {}", baseUrl, model, temperature);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException(
                    "OpenAI API Key is required. Set OPENAI_API_KEY environment variable or spring.ai.openai.api-key property.");
        }
        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(apiKey)
                        .baseUrl(baseUrl)
                        .model(model)
                        .temperature(temperature)
                        .timeout(timeout)
                        .build())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))  // ← 关键
                .build();
    }
}
