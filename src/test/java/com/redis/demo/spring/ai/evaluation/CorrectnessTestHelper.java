package com.redis.demo.spring.ai.evaluation;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 正确性评估测试用例辅助类
 * 用于加载和管理正确性评估测试数据
 */
public class CorrectnessTestHelper {

    private static final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * 从资源文件加载正确性测试用例
     *
     * @param resource JSON资源文件
     * @return 测试用例列表
     */
    public static List<CorrectnessTestCase> loadTestCases(Resource resource) {
        try {
            String json = FileCopyUtils
                    .copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            CorrectnessTestData data = objectMapper.readValue(json, CorrectnessTestData.class);
            return data.getTestCases();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load correctness test cases", e);
        }
    }

    /**
     * 正确性测试数据包装类
     */
    public static class CorrectnessTestData {
        private String description;
        private List<CorrectnessTestCase> testCases;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<CorrectnessTestCase> getTestCases() {
            return testCases;
        }

        public void setTestCases(List<CorrectnessTestCase> testCases) {
            this.testCases = testCases;
        }
    }

    /**
     * 正确性测试用例
     * 包含问题、上下文和期望的正确答案
     */
    public static class CorrectnessTestCase {
        private String id;
        private String question;
        private String context;
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

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
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

    /**
     * 评估结果封装类
     * 包含相关性、事实性和综合正确性得分
     */
    public static class EvaluationResult {
        private double relevanceScore;
        private double faithfulnessScore;
        private double correctnessScore;
        private String details;

        public EvaluationResult(double relevanceScore, double faithfulnessScore) {
            this.relevanceScore = relevanceScore;
            this.faithfulnessScore = faithfulnessScore;
            // 正确性得分 = 相关性权重(0.4) * 相关性得分 + 事实性权重(0.6) * 事实性得分
            this.correctnessScore = 0.4 * relevanceScore + 0.6 * faithfulnessScore;
        }

        public double getRelevanceScore() {
            return relevanceScore;
        }

        public double getFaithfulnessScore() {
            return faithfulnessScore;
        }

        public double getCorrectnessScore() {
            return correctnessScore;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

        @Override
        public String toString() {
            return String.format("Correctness: %.2f (Relevance: %.2f, Faithfulness: %.2f)",
                    correctnessScore, relevanceScore, faithfulnessScore);
        }
    }
}
