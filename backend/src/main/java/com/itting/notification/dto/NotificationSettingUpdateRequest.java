package com.itting.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PATCH /api/v1/me/notification-settings}의 본문. 다섯 값을 모두 보낸다. 바꾸지 않은 갈래도 지금 값을 담는다.
 * 키가 빠지면 null이 되어 {@code @NotNull}에 걸린다.
 */
public record NotificationSettingUpdateRequest(
		@Schema(description = "모임 알림을 켤지", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = REQUIRED) Boolean isMeetupOn,
		@Schema(description = "채팅 알림을 켤지", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = REQUIRED) Boolean isChatOn,
		@Schema(description = "커뮤니티 알림을 켤지", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = REQUIRED) Boolean isCommunityOn,
		@Schema(description = "땅굴 알림을 켤지", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = REQUIRED) Boolean isBurrowOn,
		@Schema(description = "공지 알림을 켤지", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = REQUIRED) Boolean isNoticeOn) {

	static final String REQUIRED = "필수 값입니다.";

}
