package com.itting.notification.dto;

import java.time.Instant;

import com.itting.notification.domain.NotificationCategory;
import com.itting.notification.domain.NotificationTargetType;
import com.itting.notification.domain.NotificationType;
import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 알림 한 줄({@code docs/api/09-notification.md}의 "알림의 모양"). 알림 팝업의 최근 알림과 알림 목록이 함께 쓴다.
 * 제목과 보조 설명은 프론트엔드가 {@code type}, {@code actor.nickname}, {@code target.title}로 만든다.
 */
public record NotificationItemResponse(
		@Schema(description = "알림의 id", example = "7201") Long id,
		@Schema(description = "알림의 종류", example = "POST_COMMENTED") NotificationType type,
		@Schema(description = "갈래. 종류 아이콘과 색을 정한다", example = "COMMUNITY") NotificationCategory category,
		@Schema(description = "알림을 일으킨 사람. 없거나 드러내지 않는 알림은 null") UserDisplayResponse actor,
		@Schema(description = "눌렀을 때 갈 곳") TargetResponse target,
		@Schema(description = "읽은 알림인지", example = "false") boolean isRead,
		@Schema(description = "알림이 생긴 시각", example = "2026-11-19T03:02:00Z") Instant createdAt) {

	public record TargetResponse(
			@Schema(description = "갈 곳의 종류", example = "POST") NotificationTargetType type,
			@Schema(description = "갈 곳의 id. 내 땅굴의 방명록(GUESTBOOK)이면 null", example = "204") Long id,
			@Schema(description = "갈 곳의 이름. 모임 이름, 글 제목, 공지 제목. 그 밖이거나 원본이 사라졌으면 null",
					example = "토요일 아침 한강 러닝 같이 하실 분") String title,
			@Schema(description = "갈 곳이 모임일 때 시작 시각. 그 밖이거나 모임이 삭제됐으면 null") Instant startsAt) {
	}

}
