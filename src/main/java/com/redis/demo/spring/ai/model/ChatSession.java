package com.redis.demo.spring.ai.model;

/**
 * 会话初始化响应 DTO。
 * 前端以 response.chatId 读取会话 ID，再拼接到 /chat/{chatId} 发起对话，
 * 因此字段名必须是 chatId，不能复用 Message 的 message 字段。
 */
public record ChatSession(String chatId) {
}
