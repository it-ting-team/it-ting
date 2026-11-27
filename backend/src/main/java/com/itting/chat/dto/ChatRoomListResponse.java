package com.itting.chat.dto;

import java.util.List;

import com.itting.chat.domain.ChatRoomType;
import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/me/chat-rooms}의 {@code data}.
 */
public record ChatRoomListResponse(
		@Schema(description = "내가 들어가 있는 대화방. 마지막 메시지가 새로운 것부터. 한 번에 모두 준다")
		List<ChatRoomSummary> items) {

	/**
	 * 대화 목록의 한 줄.
	 */
	public record ChatRoomSummary(
			@Schema(description = "대화방의 id", example = "15") Long id,
			@Schema(description = "DIRECT(1:1), MEETUP(모임) 가운데 하나", example = "DIRECT") ChatRoomType type,
			@Schema(description = "1:1 대화 상대의 사용자 표시. 모임 대화방이면 null") UserDisplayResponse counterpart,
			@Schema(description = "모임 대화방의 모임. 1:1 대화방이면 null") MeetupRef meetup,
			@Schema(description = "마지막 메시지. 메시지가 하나도 없으면 null") ChatMessageResponse lastMessage,
			@Schema(description = "안 읽은 수. 남이 보낸 사람 메시지만 센다", example = "1") long unreadCount,
			@Schema(description = "읽기 전용인지. 1:1 대화방에서 상대가 나갔거나 탈퇴했을 때 true. 모임 대화방은 항상 false",
					example = "false")
			boolean isReadOnly) {
	}

	/** 모임 대화방의 모임. 모임 이름을 대화방의 이름으로 보여 준다. */
	public record MeetupRef(
			@Schema(description = "모임의 id", example = "12") Long id,
			@Schema(description = "모임 이름", example = "알고리즘 스터디") String title) {
	}

}
