package com.itting.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/v1/chat-rooms/{chatRoomId}/read}의 본문.
 *
 * @param messageId 화면에 보인 마지막 메시지의 id. 이 대화방의 메시지여야 한다
 */
public record ChatReadRequest(
		@Schema(description = "화면에 보인 마지막 메시지의 id", example = "4102", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "필수 값입니다.") Long messageId) {
}
