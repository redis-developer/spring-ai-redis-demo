package com.redis.demo.spring.ai;

import java.io.IOException;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.autoconfigure.vectorstore.redis.RedisVectorStoreProperties;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.vectorstore.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class RagDataLoader implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(RagDataLoader.class);

	@Value("${datafile:https://storage.googleapis.com/jrx/beers_desc.json.gz}")
	private Resource dataset;

	@Value("${skipload:false}")
	private boolean skipLoad;

	private final RedisVectorStore vectorStore;

	private final RedisVectorStoreProperties properties;

	public RagDataLoader(RedisVectorStore vectorStore, RedisVectorStoreProperties properties) {
		this.vectorStore = vectorStore;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		if (skipLoad) {
			return;
		}
		Map<String, Object> indexInfo = vectorStore.getJedis().ftInfo(properties.getIndex());
		int numDocs = Integer.parseInt((String) indexInfo.getOrDefault("num_docs", "0"));
		if (numDocs > 20000) {
			return;
		}
		logger.info("Creating Embeddings...");
		JsonReader jsonLoader = new JsonReader(resource(), "name", "abv", "ibu", "description");
		vectorStore.add(jsonLoader.get());
		logger.info("Embeddings created.");
	}

	private Resource resource() throws IOException {
		if (dataset.getFilename().endsWith(".gz")) {
			GZIPInputStream inputStream = new GZIPInputStream(dataset.getInputStream());
			return new InputStreamResource(inputStream, "beers_desc.json.gz");
		}
		return dataset;
	}

}
