package com.itting.auth.domain;

import java.time.Instant;

/**
 * 가입 진행 기록({@code docs/api/02-auth.md}의 "가입 진행"). 계정을 만들기 전까지 Redis에 두고, 가입 쿠키로 찾는다.
 *
 * @param token 가입 쿠키의 값
 * @param method 가입 방법
 * @param email 이메일 가입에서 인증한 이메일
 * @param expiresAt 이 시각부터 가입을 이어 갈 수 없다
 * @param generation SSAFY 인증 코드를 보낸 기수. 보내기 전에는 null
 * @param mattermostId SSAFY 인증 코드를 보낸 Mattermost ID. 보내기 전에는 null
 * @param ssafyCode 보낸 SSAFY 인증 코드. 보내기 전이거나 너무 많이 틀려 지웠으면 null
 * @param ssafySentAt SSAFY 인증 코드를 마지막으로 보낸 시각(재전송 간격). 보내기 전에는 null
 */
public record SignupRecord(
		String token,
		SignupMethod method,
		String email,
		Instant expiresAt,
		Integer generation,
		String mattermostId,
		VerificationCode ssafyCode,
		Instant ssafySentAt) {

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

}
