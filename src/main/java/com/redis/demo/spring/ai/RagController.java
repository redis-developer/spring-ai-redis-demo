package com.redis.demo.spring.ai;

import java.util.UUID;

import org.springframework.ai.chat.Generation;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RagController {

	private final RagService ragService;

	public RagController(RagService ragService) {
		this.ragService = ragService;
	}

	@PostMapping("/chat/startChat")
	@ResponseBody
	public Message startChat() {
		return Message.of(UUID.randomUUID().toString());
	}

	//tag::chatMessage[]
	@PostMapping("/chat/{chatId}")
	@ResponseBody
	public Message chatMessage(@PathVariable("chatId") String chatId, @RequestBody Prompt prompt) {
		// Extract user prompt from the body and pass it to the RagService
		Generation generation = ragService.retrieve(prompt.getPrompt());
		// Reply with the generated message
		return Message.of(generation.getOutput().getContent());
	}
	//end::chatMessage[]

	@PostMapping("/documents/upload")
	@ResponseBody
	public String uploadDocument(String doc) {
		return "Document upload not supported";
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
