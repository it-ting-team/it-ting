package com.itting.chat.dto;

import java.time.Instant;

import com.itting.chat.domain.ChatRequest;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/chat-requests}의 {@code data}. 요청을 새로 만들었을 때와 내가 거절한 요청이 남아 있는 사람에게 보내
 * 대화방이 바로 열렸을 때가 같은 모양이다.
 */
public record ChatRequestCreateResponse(
		@Schema(description = "만든 대화 요청의 id. 대화방이 바로 열렸으면 null", example = "88") Long chatRequestId,
		@Schema(description = "보낸 시각. 대화방이 바로 열렸으면 null") Instant createdAt,
		@Schema(description = "요청이 만료되는 시각. 대화방이 바로 열렸으면 null") Instant expiresAt,
		@Schema(description = "바로 열린 1:1 대화방의 id. 요청을 새로 만들었으면 null", example = "16") Long chatRoomId) {

	/** 요청을 새로 만들었다. */
	public static ChatRequestCreateResponse sent(ChatRequest request) {
		return new ChatRequestCreateResponse(request.getId(), request.getCreatedAt(), request.expiresAt(), null);
	}

	/** 요청을 만들지 않고 대화방을 바로 열었다. */
	public static ChatRequestCreateResponse opened(Long chatRoomId) {
		return new ChatRequestCreateResponse(null, null, null, chatRoomId);
	}

}
