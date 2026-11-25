package com.itting.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/me/chat-requests/count}의 {@code data}. 요청 목록의 개수와 늘 같다.
 */
public record ChatRequestCountResponse(
		@Schema(description = "내가 받은, 아직 답하지 않은 요청 수. 채팅의 \"요청 N\"의 N", example = "2") long receivedCount,
		@Schema(description = "내가 보낸 살아 있는 요청 수", example = "1") long sentCount) {
}
