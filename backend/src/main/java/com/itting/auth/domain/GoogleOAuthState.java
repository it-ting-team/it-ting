package com.itting.auth.domain;

import java.time.Instant;

/**
 * Google로 보낸 시도 하나({@code docs/api/02-auth.md}의 "구현하며 정한 것"). 시작할 때 Redis에 두고 콜백에서 한 번 읽고 지운다.
 *
 * @param purpose 이 시도의 이유
 * @param userId 설정에서 시작한 시도({@code LINK}, {@code WITHDRAW})면 시작한 계정. 로그인이면 null
 * @param sessionId 설정에서 시작한 시도면 시작한 로그인 세션의 id. 로그인이면 null
 * @param codeVerifier PKCE의 {@code code_verifier}. 콜백에서 토큰을 받을 때 Google로 보낸다
 * @param expiresAt 이 시각부터 이 시도로 돌아올 수 없다
 */
public record GoogleOAuthState(
		GoogleOAuthPurpose purpose,
		Long userId,
		String sessionId,
		String codeVerifier,
		Instant expiresAt) {

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

}
