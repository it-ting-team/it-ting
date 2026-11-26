package com.itting.chat.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/chat-rooms/{chatRoomId}/messages}의 {@code data}. 커서 방식으로 새 것부터 나눠 준다.
 */
public record ChatMessageListResponse(
		@Schema(description = "메시지. 새 것부터(id가 큰 것부터). 사람이 보낸 메시지와 안내 메시지를 함께 준다")
		List<ChatMessageResponse> items,
		@Schema(description = "더 오래된 메시지를 받을 때 보낼 값. 더 없으면 null") String nextCursor,
		@Schema(description = "더 오래된 메시지가 있는지", example = "false") boolean hasNext) {
}
