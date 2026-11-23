package com.itting.notification.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/me/notifications}의 {@code data}.
 */
public record NotificationListResponse(
		@Schema(description = "알림 목록. 안 읽은 것 먼저, 각각 새것 먼저") List<NotificationItemResponse> items,
		@Schema(description = "다음 알림을 받을 때 보낼 값. 더 없으면 null") String nextCursor,
		@Schema(description = "더 받을 알림이 있는지", example = "false") boolean hasNext) {
}
