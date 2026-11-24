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

/**
 * 忠实性评估测试用例辅助类
 * 用于加载和管理忠实性评估测试数据
 */
public class FaithfulnessTestHelper {

    private static final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * 从资源文件加载忠实性测试用例
     *
     * @param resource JSON资源文件
     * @return 测试用例列表
     */
    public static List<FaithfulnessTestCase> loadTestCases(Resource resource) {
        try {
            String json = FileCopyUtils
                    .copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            FaithfulnessTestData data = objectMapper.readValue(json, FaithfulnessTestData.class);
            return data.getTestCases();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load faithfulness test cases", e);
        }
    }

    /**
     * 忠实性测试数据包装类
     */
    public static class FaithfulnessTestData {
        private String description;
        private List<FaithfulnessTestCase> testCases;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<FaithfulnessTestCase> getTestCases() {
            return testCases;
        }

        public void setTestCases(List<FaithfulnessTestCase> testCases) {
            this.testCases = testCases;
        }
    }

    /**
     * 忠实性测试用例
     * 包含问题、上下文、忠实回答和不忠实回答
     */
    public static class FaithfulnessTestCase {
        private String id;
        private String question;
        private String context;
        private String faithfulAnswer;
        private String unfaithfulAnswer;
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

        public String getFaithfulAnswer() {
            return faithfulAnswer;
        }

        public void setFaithfulAnswer(String faithfulAnswer) {
            this.faithfulAnswer = faithfulAnswer;
        }

        public String getUnfaithfulAnswer() {
            return unfaithfulAnswer;
        }

        public void setUnfaithfulAnswer(String unfaithfulAnswer) {
            this.unfaithfulAnswer = unfaithfulAnswer;
        }

        public String getScenario() {
            return scenario;
        }

        public void setScenario(String scenario) {
            this.scenario = scenario;
        }
    }
}
