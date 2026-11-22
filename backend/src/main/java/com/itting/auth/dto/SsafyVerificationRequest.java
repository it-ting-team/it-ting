package com.itting.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * {@code POST /api/v1/ssafy-verifications}의 본문.
 *
 * @param generation 기수. {@code GET /api/v1/generations}가 준 값 가운데 하나
 * @param mattermostId SSAFY Mattermost ID. 앞뒤 공백과 앞의 {@code @}를 떼고 소문자로 바꾼다.
 *        Mattermost 아이디 규칙(영문 소문자 · 숫자 · {@code .} · {@code -} · {@code _}, 3 ~ 22자)을 따른다
 */
public record SsafyVerificationRequest(
		@NotNull Integer generation,
		@NotBlank @Pattern(regexp = "[a-z0-9._-]{3,22}", message = "Mattermost ID 형식이 아닙니다.") String mattermostId) {

	public SsafyVerificationRequest {
		if (mattermostId != null) {
			String id = mattermostId.strip();
			mattermostId = (id.startsWith("@") ? id.substring(1) : id).toLowerCase(Locale.ROOT);
		}
	}

}
