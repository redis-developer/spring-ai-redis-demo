package com.redis.demo.spring.ai;

import org.springframework.ai.chat.Generation;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.github.f4b6a3.ulid.UlidCreator;

@Controller
public class RagController {

	private final RagService ragService;

	public RagController(RagService ragService) {
		this.ragService = ragService;
	}

	@PostMapping("/chat/startChat")
	@ResponseBody
	public Message startChat() {
		return Message.of(UlidCreator.getUlid().toLowerCase());
	}

	@PostMapping("/chat/{chatId}")
	@ResponseBody
	public Message chatMessage(@PathVariable("chatId") String chatId, @RequestBody Prompt prompt) {
		Generation generation = ragService.retrieve(prompt.getPrompt());
		return Message.of(generation.getOutput().getContent());
	}

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
