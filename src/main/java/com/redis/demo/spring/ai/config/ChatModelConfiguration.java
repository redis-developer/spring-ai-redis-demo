package com.redis.demo.spring.ai.config;

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
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

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
     */
    @Bean
    @ConditionalOnProperty(name = "chat.provider", havingValue = "ollama", matchIfMissing = true)
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        logger.info("Creating OllamaApi with baseUrl: {}", baseUrl);
        return OllamaApi.builder().baseUrl(baseUrl).build();
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
                .defaultOptions(OllamaChatOptions.builder().model(model).build())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))  // ← 关键
                .modelManagementOptions(ModelManagementOptions.builder()
                        .timeout(readTimeout)
                        .build())
                .build();
    }

    /**
     * OpenAiApi Bean - 用于创建 OpenAI ChatModel
     */
    @Bean
    @ConditionalOnProperty(name = "chat.provider", havingValue = "openai")
    public OpenAiApi openAiApi(
            RestClient.Builder restClientBuilder,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${SILICONFLOW_KEY:${spring.ai.openai.api-key:${OPENAI_API_KEY:}}}") String apiKey) {
        logger.info("Creating OpenAiApi with baseUrl: {}", baseUrl);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException(
                    "OpenAI API Key is required. Set OPENAI_API_KEY environment variable or spring.ai.openai.api-key property.");
        }
        // 注入容器里的 RestClient.Builder，使 RestClientCustomizer 中配置的连接/读取超时真正生效；
        // 若直接用 OpenAiApi.builder() 的默认 RestClient，上游卡住时会没有任何超时保护。
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();
    }

    /**
     * OpenAI ChatModel - 当 chat.provider=openai 时创建
     * 支持官方 OpenAI API 以及兼容 API（如硅基流动等）
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "chat.provider", havingValue = "openai")
    public ChatModel openAiChatModel(
            OpenAiApi openAiApi,
            ObjectProvider<ObservationRegistry> observationRegistry,   // ← 注入容器里的 registry
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
            @Value("${spring.ai.openai.chat.options.temperature:0.7}") Double temperature) {
        logger.info("Initializing OpenAiChatModel with model: {}, temperature: {}", model, temperature);
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .build())
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))  // ← 关键
                .build();
    }
}
