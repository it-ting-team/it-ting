package com.itting.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code PUT /api/v1/chat-rooms/{chatRoomId}/notice}의 {@code data}.
 */
public record ChatNoticeResponse(
		@Schema(description = "저장한 공지", example = "매주 토요일 오후 2시에 모여요. 노트북을 가져오세요.") String notice) {
}
