package com.itting.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/chat-rooms/{chatRoomId}/read}의 {@code data}.
 */
public record ChatReadResponse(
		@Schema(description = "기록한 뒤 이 대화방의 안 읽은 수", example = "0") long unreadCount) {
}
