package com.itting.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 안 읽은 알림 수와 빨간 점. 안 읽은 수 API와 읽음 API 두 개의 {@code data}다.
 */
public record NotificationUnreadCountResponse(
		@Schema(description = "안 읽은 알림 수. 모든 갈래를 센다", example = "4") long unreadCount,
		@Schema(description = "GNB 알림 아이콘의 빨간 점을 켤지. 지금 켜 둔 갈래의 안 읽은 알림만 본다", example = "true")
		boolean hasUnread) {
}
