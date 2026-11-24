package com.itting.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/me/notification-settings}의 {@code data}. 갈래별로 켰는지와 푸시 공개 키다.
 * "브라우저 알림 받기" 스위치의 상태는 주지 않는다. 프론트엔드가 이 브라우저의 알림 권한과 구독으로 정한다.
 */
public record NotificationSettingResponse(
		@Schema(description = "모임 알림을 켰는지", example = "true") boolean isMeetupOn,
		@Schema(description = "채팅 알림을 켰는지", example = "true") boolean isChatOn,
		@Schema(description = "커뮤니티 알림을 켰는지", example = "false") boolean isCommunityOn,
		@Schema(description = "땅굴 알림을 켰는지", example = "true") boolean isBurrowOn,
		@Schema(description = "공지 알림을 켰는지", example = "true") boolean isNoticeOn,
		@Schema(description = "브라우저가 푸시 구독을 만들 때 쓰는 공개 키. 아직 정하지 않았으면 null(브라우저 푸시를 만드는 KAN-59 전까지)",
				example = "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U",
				nullable = true) String pushPublicKey) {
}
