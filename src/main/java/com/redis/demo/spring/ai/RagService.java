package com.redis.demo.spring.ai;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

public class RagService {

	private static final Logger logger = LoggerFactory.getLogger(RagService.class);

	@Value("classpath:/prompts/system-qa.st")
	private Resource systemBeerPrompt;

	@Value("${topk:10}")
	private int topK;

	private final ChatClient chatClient;

	private final VectorStore vectorStore;

	public RagService(ChatClient chatClient, VectorStore vectorStore) {
		this.chatClient = chatClient;
		this.vectorStore = vectorStore;
	}

	public Generation retrieve(String message) {
		logger.info("Retrieving relevant documents");
		SearchRequest request = SearchRequest.query(message).withTopK(topK);
		List<Document> similarDocuments = vectorStore.similaritySearch(request);
		if (logger.isInfoEnabled()) {
			logger.info(String.format("Found %s relevant documents.", similarDocuments.size()));
		}
		Message systemMessage = getSystemMessage(similarDocuments);
		UserMessage userMessage = new UserMessage(message);
		logger.info("Asking AI model to reply to question.");
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		if (logger.isInfoEnabled()) {
			logger.info(prompt.toString());
		}
		ChatResponse response = chatClient.call(prompt);
		logger.info("AI responded.");
		if (logger.isInfoEnabled()) {
			logger.info(response.getResult().toString());
		}
		return response.getResult();
	}

	private Message getSystemMessage(List<Document> similarDocuments) {
		String documents = similarDocuments.stream().map(Document::getContent).collect(Collectors.joining("\n"));
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemBeerPrompt);
		return systemPromptTemplate.createMessage(Map.of("documents", documents));
	}

}
