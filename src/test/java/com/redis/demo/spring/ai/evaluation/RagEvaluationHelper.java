package com.redis.demo.spring.ai.evaluation;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class RagEvaluationHelper {

    private static final ObjectMapper objectMapper = JsonMapper.builder().build();

    public static List<TestCase> loadTestCases(Resource resource) {
        try {
            String json = FileCopyUtils
                    .copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            Map<String, List<TestCase>> data = objectMapper.readValue(json, new TypeReference<>() {
            });
            return data.get("testCases");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test cases", e);
        }
    }

    public static class TestCase {
        private String id;
        private String question;
        private String expectedContext;
        private String expectedAnswer;
        private String scenario;

        // Getters and Setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getExpectedContext() {
            return expectedContext;
        }

        public void setExpectedContext(String expectedContext) {
            this.expectedContext = expectedContext;
        }

        public String getExpectedAnswer() {
            return expectedAnswer;
        }

        public void setExpectedAnswer(String expectedAnswer) {
            this.expectedAnswer = expectedAnswer;
        }

        public String getScenario() {
            return scenario;
        }

        public void setScenario(String scenario) {
            this.scenario = scenario;
        }
    }
}
