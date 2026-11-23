package com.itting.notification.dto;

import java.util.List;

import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/me/notifications/popup}의 {@code data}.
 */
public record NotificationPopupResponse(
		@Schema(description = "내가 연 모임에 온, 아직 처리하지 않은 참여 신청의 수", example = "1") long meetupRequestCount,
		@Schema(description = "내가 받은, 아직 답하지 않은 대화 요청의 수", example = "3") long chatRequestCount,
		@Schema(description = "가장 최근에 대화 요청을 보낸 사람. 요청이 없으면 null") UserDisplayResponse latestChatRequester,
		@Schema(description = "최근 알림 5개까지. 요청 알림은 빼고, 안 읽은 것 먼저") List<NotificationItemResponse> recentItems,
		@Schema(description = "안 읽은 알림 수", example = "4") long unreadCount) {
}
