package com.itting.auth.domain;

import java.time.Instant;

/**
 * Google 확인 기록({@code docs/api/10-settings.md}의 "Google 확인"). 설정에서 Google 계정을 확인받으면 로그인 세션에 묶어
 * Redis에 둔다. {@code LINK} 기록은 {@code PUT /api/v1/me/google-link}가, {@code WITHDRAW} 기록은 탈퇴 API가 쓴다.
 *
 * @param purpose 무엇을 위해 확인받았나({@code LINK} 또는 {@code WITHDRAW})
 * @param googleSub 확인받은 Google 계정 식별자
 * @param googleEmail 확인받은 Google 이메일
 * @param expiresAt 이 시각부터 쓸 수 없다
 */
public record GoogleVerification(
		GoogleOAuthPurpose purpose,
		String googleSub,
		String googleEmail,
		Instant expiresAt) {

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

}
