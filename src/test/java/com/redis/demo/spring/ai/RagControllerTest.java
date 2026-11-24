package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.model.Bm25RebuildResponse;
import com.redis.demo.spring.ai.model.Bm25StatusResponse;
import com.redis.demo.spring.ai.model.RetrievalResult;
import com.redis.demo.spring.ai.service.Bm25AdminService;
import com.redis.demo.spring.ai.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagController.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagService ragService;

    @MockitoBean
    private Bm25AdminService bm25AdminService;

    @Test
    void retrieveShouldReturnRetrievalResults() throws Exception {
        // 准备测试数据
        Map<String, String> payload = Map.of("query", "test query", "topK", "5");
        List<RetrievalResult> mockResults = List.of(
            new RetrievalResult("id1", "content1", 0.8, Map.of("filename", "file1.pdf")),
            new RetrievalResult("id2", "content2", 0.7, Map.of("filename", "file2.pdf"))
        );

        // 模拟服务调用
        when(ragService.retrieveForTesting(any(Map.class))).thenReturn(mockResults);

        // 执行请求并验证响应
        mockMvc.perform(post("/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"test query\",\"topK\":\"5\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("id1"))
                .andExpect(jsonPath("$[0].content").value("content1"))
                .andExpect(jsonPath("$[0].score").value(0.8))
                .andExpect(jsonPath("$[1].id").value("id2"))
                .andExpect(jsonPath("$[1].content").value("content2"))
                .andExpect(jsonPath("$[1].score").value(0.7));
    }

    @Test
    void retrieveShouldHandleMinimalPayload() throws Exception {
        // 准备测试数据 - 最小有效载荷（仅包含查询）
        Map<String, String> payload = Map.of("query", "minimal query");
        List<RetrievalResult> mockResults = List.of(
            new RetrievalResult("id1", "content1", 0.8, Map.of("filename", "file1.pdf"))
        );

        // 模拟服务调用
        when(ragService.retrieveForTesting(any(Map.class))).thenReturn(mockResults);

        // 执行请求并验证响应
        mockMvc.perform(post("/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"minimal query\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("id1"))
                .andExpect(jsonPath("$[0].content").value("content1"))
                .andExpect(jsonPath("$[0].score").value(0.8));
    }

    @Test
    void retrieveShouldHandleEmptyPayload() throws Exception {
        // 准备测试数据 - 空载荷
        List<RetrievalResult> mockResults = List.of();

        // 模拟服务调用
        when(ragService.retrieveForTesting(any(Map.class))).thenReturn(mockResults);

        // 执行请求并验证响应
        mockMvc.perform(post("/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void startChatShouldReturnChatIdReadByFrontend() throws Exception {
        // 前端 ChatWindow.start() 用 response.chatId 拼 /chat/{chatId}，
        // 字段名不再是 chatId 时会退化成请求 /chat/undefined
        mockMvc.perform(post("/chat/startChat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatId").isString())
                .andExpect(jsonPath("$.chatId").isNotEmpty());
    }

    @Test
    void bm25StatusShouldReturnCountersAndRecommendation() throws Exception {
        when(bm25AdminService.status()).thenReturn(new Bm25StatusResponse(
                false, false, 0, false, false, 0L, 42, false, false,
                "BM25 index missing while Milvus has data; call POST /admin/bm25/rebuild"));

        mockMvc.perform(get("/admin/bm25/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.hybridEnabled").value(false))
                .andExpect(jsonPath("$.indexBuilt").value(false))
                .andExpect(jsonPath("$.memCount").value(0))
                .andExpect(jsonPath("$.redisCount").value(0))
                .andExpect(jsonPath("$.milvusCount").value(42))
                .andExpect(jsonPath("$.healthy").value(false))
                .andExpect(jsonPath("$.recommendation").exists());
    }

    @Test
    void bm25RebuildShouldReturnRebuiltCount() throws Exception {
        when(bm25AdminService.rebuild()).thenReturn(Bm25RebuildResponse.success(7));

        mockMvc.perform(post("/admin/bm25/rebuild"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.bm25DocumentCount").value(7));
    }

    @Test
    void bm25RebuildShouldReturn500OnFailure() throws Exception {
        when(bm25AdminService.rebuild()).thenReturn(Bm25RebuildResponse.failure("Hybrid search is disabled"));

        mockMvc.perform(post("/admin/bm25/rebuild"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists());
    }
}