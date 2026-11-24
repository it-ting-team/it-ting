package com.itting.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/chat-requests/{chatRequestId}/accept}의 {@code data}.
 */
public record ChatRequestAcceptResponse(
		@Schema(description = "만들어진 1:1 대화방의 id. 이미 수락한 요청이면 그때 만든 대화방의 id", example = "16") Long chatRoomId) {
}
