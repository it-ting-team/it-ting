package com.itting.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/me/push-subscriptions}의 응답. 새로 등록했으면 {@code 201}, 이미 등록돼 있던 브라우저면 {@code 200}이다.
 */
public record PushSubscriptionResponse(
		@Schema(description = "등록의 id. 푸시를 끌 때 쓴다", example = "37") Long pushSubscriptionId) {
}
