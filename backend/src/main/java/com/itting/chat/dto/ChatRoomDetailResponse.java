package com.itting.chat.dto;

import java.time.Instant;
import java.util.List;

import com.itting.chat.domain.ChatLeaveType;
import com.itting.chat.domain.ChatRoomType;
import com.itting.interest.dto.InterestTagResponse;
import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupMode;
import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/chat-rooms/{chatRoomId}}의 {@code data}. 찻방, 대화 창의 헤더, 공지, 모임 정보 팝업이 이 값을 쓴다.
 * 1:1 대화방은 {@code counterpart}와 {@code sharedInterests}를, 모임 대화방은 {@code meetup}, {@code notice},
 * {@code members}를 채운다. 메시지와 안 읽은 수는 담지 않는다.
 */
public record ChatRoomDetailResponse(
		@Schema(description = "대화방의 id", example = "21") Long id,
		@Schema(description = "DIRECT(1:1), MEETUP(모임) 가운데 하나", example = "MEETUP") ChatRoomType type,
		@Schema(description = "읽기 전용인지. 1:1 대화방에서 상대가 나갔거나 탈퇴했을 때 true", example = "false")
		boolean isReadOnly,
		@Schema(description = "나가기 버튼이 지금 하는 일. LEAVE_ROOM(채팅의 나가기를 부른다), "
				+ "CANCEL_PARTICIPATION(모임의 참여 취소를 부른다), NOT_ALLOWED(나갈 수 없다)", example = "LEAVE_ROOM")
		ChatLeaveType leaveType,
		@Schema(description = "1:1 대화 상대의 사용자 표시. 모임 대화방이면 null") UserDisplayResponse counterpart,
		@Schema(description = "상대와 내가 함께 고른 관심사 태그. 상대가 고른 순서. 모임 대화방이거나 상대가 탈퇴했으면 []")
		List<InterestTagResponse> sharedInterests,
		@Schema(description = "모임 대화방의 모임. 1:1 대화방이면 null") MeetupInfo meetup,
		@Schema(description = "모임 대화방의 공지. 없으면 null. 1:1 대화방은 항상 null") String notice,
		@Schema(description = "내가 공지를 쓰고 고치고 지울 수 있는지. 모임 대화방의 주최자만 true", example = "false")
		boolean canEditNotice,
		@Schema(description = "지금 대화방에 있는 사람. 주최자가 맨 앞, 그다음은 들어온 순서. 1:1 대화방이면 []")
		List<Member> members) {

	/**
	 * 모임 대화방의 모임. 헤더와 모임 정보 팝업이 쓴다.
	 */
	public record MeetupInfo(
			@Schema(description = "모임의 id", example = "12") Long id,
			@Schema(description = "주최자의 계정 id", example = "31") Long hostId,
			@Schema(description = "모임 이름", example = "알고리즘 스터디") String title,
			@Schema(description = "모임의 카테고리", example = "STUDY") MeetupCategory category,
			@Schema(description = "시작 시각") Instant startsAt,
			@Schema(description = "끝나는 시각. 정하지 않은 모임은 null") Instant endsAt,
			@Schema(description = "OFFLINE, ONLINE 가운데 하나", example = "OFFLINE") MeetupMode mode,
			@Schema(description = "오프라인 모임의 장소. 온라인 모임은 null", example = "서울 캠퍼스 5층 라운지") String place,
			@Schema(description = "상세 주소. 없으면 null") String placeDetail,
			@Schema(description = "온라인 모임의 접속 링크. 없으면 null") String joinUrl,
			@Schema(description = "정원. 주최자 포함", example = "6") int capacity,
			@Schema(description = "확정 인원. 주최자 포함", example = "4") long confirmedCount,
			@Schema(description = "취소된 모임인지", example = "false") boolean isCanceled) {
	}

	/** 지금 대화방에 있는 사람 한 명. */
	public record Member(
			@Schema(description = "그 사람의 사용자 표시") UserDisplayResponse user,
			@Schema(description = "주최자인지", example = "true") boolean isHost,
			@Schema(description = "나인지", example = "false") boolean isMe) {
	}

}
