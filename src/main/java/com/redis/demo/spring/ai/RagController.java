package com.redis.demo.spring.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.redis.demo.spring.ai.model.Bm25RebuildResponse;
import com.redis.demo.spring.ai.model.Bm25StatusResponse;
import com.redis.demo.spring.ai.model.ChatSession;
import com.redis.demo.spring.ai.model.RetrievalResult;
import com.redis.demo.spring.ai.service.Bm25AdminService;
import com.redis.demo.spring.ai.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RagController {

	private final RagService ragService;
	private final Bm25AdminService bm25AdminService;

	public RagController(RagService ragService, Bm25AdminService bm25AdminService) {
		this.ragService = ragService;
		this.bm25AdminService = bm25AdminService;
	}

	@PostMapping("/chat/startChat")
	@ResponseBody
	public ChatSession startChat() {
		return new ChatSession(UUID.randomUUID().toString());
	}

	//tag::chatMessage[]
	@PostMapping("/chat/{chatId}")
// 1. 移除 @ResponseBody (ResponseEntity 包含了响应体逻辑)
	public CompletableFuture<ResponseEntity<Message>> chatMessage(
			@PathVariable("chatId") String chatId,
			@RequestBody Prompt prompt) { // 假设 Prompt 是您的 DTO

		// 2. 开启异步任务，不占用 Tomcat 线程
		return CompletableFuture.supplyAsync(() -> {
					// 这里执行耗时操作 (25s+)
					// 注意：请确保 ragService 内部调用的 Client 超时设置已大于 60s
					// 假设 prompt.getContents() 获取文本，请根据实际对象调整
					return ragService.retrieve(prompt.getPrompt());
				})
				// 3. 设置 Java 层面的超时 (JDK 9+ 支持 orTimeout)
				// 必须小于 spring.mvc.async.request-timeout(90s)、大于上游 HTTP 读取超时(60s)，
				// 否则容器会先抛 AsyncRequestTimeoutException（对外表现为 503），这里的友好错误信息永远回不到前端
				.orTimeout(65, TimeUnit.SECONDS)

				// 4. 成功时的处理
				.thenApply(generation -> {
					Message message = Message.of(generation.getOutput().getText());
					return ResponseEntity.ok(message);
				})

				// 5. 异常或超时时的处理
				.exceptionally(ex -> {
					// 区分是超时还是其他错误
					String errorMsg = "处理请求失败";
					if (ex instanceof java.util.concurrent.TimeoutException) {
						errorMsg = "LLM 服务响应超时，请稍后重试";
					} else {
						errorMsg = "错误: " + ex.getMessage();
					}
					return ResponseEntity.status(500).body(Message.of(errorMsg));
				});
	}
	//end::chatMessage[]

	@PostMapping("/documents/upload")
	@ResponseBody
	public String uploadDocument(String doc) {
		return "Document upload not supported";
	}


	@PostMapping("/retrieve")
	@ResponseBody
	public List<RetrievalResult> retrieve(@RequestBody Map<String, String> payload) {
		 return ragService.retrieveForTesting(payload);
	}

	/**
	 * BM25 索引状态自检（运维）。
	 * 返回内存/Redis/Milvus 三处计数与健康、一致性判定，用于定位“BM25 静默退化为纯向量”。
	 */
	@GetMapping("/admin/bm25/status")
	@ResponseBody
	public ResponseEntity<Bm25StatusResponse> bm25Status() {
		return ResponseEntity.ok(bm25AdminService.status());
	}

	/**
	 * 运维重建 BM25 索引：全量清空内存索引与 Redis 持久化后，从原始数据源重建并重新持久化。
	 */
	@PostMapping("/admin/bm25/rebuild")
	@ResponseBody
	public ResponseEntity<Bm25RebuildResponse> bm25Rebuild() {
		Bm25RebuildResponse response = bm25AdminService.rebuild();
		return response.success()
				? ResponseEntity.ok(response)
				: ResponseEntity.internalServerError().body(response);
	}

	public static class Message {

		private String message;

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public static Message of(String message) {
			Message response = new Message();
			response.setMessage(message);
			return response;
		}

	}



	public static class Prompt {

		private String prompt;

		public String getPrompt() {
			return prompt;
		}

		public void setPrompt(String prompt) {
			this.prompt = prompt;
		}

	}

}