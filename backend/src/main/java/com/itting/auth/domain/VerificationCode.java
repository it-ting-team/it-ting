package com.itting.auth.domain;

import java.time.Instant;

/**
 * 보낸 인증 코드(이메일 · SSAFY 공통). Redis에 둔다.
 *
 * @param code 숫자 6자리
 * @param expiresAt 이 시각부터 쓸 수 없다
 * @param attempts 지금까지 틀린 횟수
 */
public record VerificationCode(String code, Instant expiresAt, int attempts) {

	public enum Result {

		MATCH,

		MISMATCH,

		/** 유효 시간이 지났다. */
		EXPIRED

	}

	/** 시간이 지났는지 먼저 보고, 그다음 코드를 비교한다. */
	public Result check(String input, Instant now) {
		if (!now.isBefore(expiresAt)) {
			return Result.EXPIRED;
		}
		return code.equals(input) ? Result.MATCH : Result.MISMATCH;
	}

}
