package com.itting.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code PATCH /api/v1/me/notification-settings}의 {@code data}. 저장한 뒤의 다섯 값이다. 푸시 공개 키는 담지 않는다.
 */
public record NotificationSettingUpdateResponse(
		@Schema(description = "모임 알림을 켰는지", example = "true") boolean isMeetupOn,
		@Schema(description = "채팅 알림을 켰는지", example = "true") boolean isChatOn,
		@Schema(description = "커뮤니티 알림을 켰는지", example = "false") boolean isCommunityOn,
		@Schema(description = "땅굴 알림을 켰는지", example = "true") boolean isBurrowOn,
		@Schema(description = "공지 알림을 켰는지", example = "true") boolean isNoticeOn) {
}
