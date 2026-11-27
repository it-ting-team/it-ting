package com.itting.chat.dto;

import java.time.Instant;
import java.util.List;

import com.itting.chat.domain.ChatMessageType;
import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 메시지 하나({@code docs/api/06-chat.md}의 "메시지"). 메시지 목록, 대화 목록의 마지막 메시지, 메시지를 보낸 뒤의 응답,
 * 실시간 소식이 모두 이 모양을 쓴다. {@code TEXT}가 아닌 것은 안내 메시지이고 문구는 프론트엔드가 {@code type}과
 * {@code sender}의 닉네임으로 만든다.
 */
public record ChatMessageResponse(
		@Schema(description = "메시지의 id. 같은 방 안에서 클수록 나중 메시지다", example = "4102") Long id,
		@Schema(description = "TEXT(사람이 보낸 메시지), ACCEPTED, JOINED, LEFT, WITHDRAWN(안내 메시지) 가운데 하나",
				example = "TEXT")
		ChatMessageType type,
		@Schema(description = "보낸 사람, 또는 안내를 일으킨 사람의 사용자 표시") UserDisplayResponse sender,
		@Schema(description = "본문. 안내 메시지와 글 없이 사진만 보낸 메시지는 null",
				example = "이번 주 토요일 아침에 한강 어때요?")
		String content,
		@Schema(description = "본문에 모임 링크가 있을 때의 모임 카드. 링크가 없거나 없는 모임 · 삭제된 모임이면 null")
		MeetupCard meetupCard,
		@Schema(description = "메시지에 붙인 사진. 보낸 순서대로이고 없으면 []") List<Image> images,
		@Schema(description = "보낸 시각, 또는 안내할 일이 일어난 시각") Instant createdAt) {

	/**
	 * 본문의 첫 모임 링크가 가리키는 모임의 지금 값. 저장하지 않고 메시지를 줄 때마다 담는다.
	 */
	public record MeetupCard(
			@Schema(description = "모임의 id", example = "19") Long meetupId,
			@Schema(description = "모임 이름", example = "토요일 한강 러닝") String title,
			@Schema(description = "모임 시작 시각") Instant startsAt,
			@Schema(description = "정원. 주최자 포함", example = "6") int capacity,
			@Schema(description = "확정 인원. 주최자 포함", example = "3") long confirmedCount) {
	}

	/** 메시지에 붙인 사진 한 장. */
	public record Image(
			@Schema(description = "사진의 id", example = "530") Long id,
			@Schema(description = "사진의 주소") String url) {
	}

}
