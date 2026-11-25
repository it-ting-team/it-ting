package com.itting.notification.dto;

import com.itting.notification.domain.PushSubscription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/me/push-subscriptions}의 본문. 브라우저가 만든 푸시 구독의 값을 그대로 보낸다.
 * 칼럼이 ASCII라, 영문 주소와 base64url 글이 아닌 값은 {@code 400 INVALID_INPUT}이다.
 */
public record PushSubscriptionRequest(
		@Schema(description = "브라우저가 준 푸시 수신 주소. https://로 시작하는 ASCII 글이고 2,000자까지",
				example = "https://fcm.googleapis.com/fcm/send/c1KrmpTuRm8:APA91bHexample",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = REQUIRED)
		@Size(max = PushSubscription.ENDPOINT_MAX, message = "2000자 이하여야 합니다.")
		@Pattern(regexp = "https://[\\x21-\\x7E]+", message = "https://로 시작하는 주소여야 합니다.") String endpoint,
		@Schema(description = "푸시를 암호화하는 키", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = REQUIRED) @Valid Keys keys) {

	static final String REQUIRED = "필수 값입니다.";

	/**
	 * @param p256dh 브라우저가 준 공개 키
	 * @param auth 브라우저가 준 인증 값
	 */
	public record Keys(
			@Schema(description = "브라우저가 준 공개 키(base64url)",
					example = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@NotBlank(message = REQUIRED)
			@Size(max = PushSubscription.KEY_MAX, message = "255자 이하여야 합니다.")
			@Pattern(regexp = KEY_PATTERN, message = "base64url 글이어야 합니다.") String p256dh,
			@Schema(description = "브라우저가 준 인증 값(base64url)", example = "tBHItJI5svbpez7KI4CCXg",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@NotBlank(message = REQUIRED)
			@Size(max = PushSubscription.KEY_MAX, message = "255자 이하여야 합니다.")
			@Pattern(regexp = KEY_PATTERN, message = "base64url 글이어야 합니다.") String auth) {

		/** base64url 글. 브라우저는 끝의 {@code =}를 붙이지 않지만 붙은 것도 받는다. */
		static final String KEY_PATTERN = "[A-Za-z0-9_-]+={0,2}";

	}

}
