package com.itting.auth.domain;

import java.util.Optional;

/**
 * Google에 다녀오는 이유. 같은 콜백 주소로 돌아오므로 시작할 때 기억해 두었다가 콜백이 갈래를 정한다.
 */
public enum GoogleOAuthPurpose {

	/** Google 로그인 · 가입({@code GET /api/v1/oauth/google}). */
	LOGIN,

	/** 설정에서 Google 계정 연동({@code GET /api/v1/oauth/google/verify?purpose=LINK}). */
	LINK,

	/** 설정에서 탈퇴 전 본인 확인({@code GET /api/v1/oauth/google/verify?purpose=WITHDRAW}). */
	WITHDRAW;

	/** {@code verify}의 {@code purpose} 쿼리. {@code LINK}와 {@code WITHDRAW}만 받는다(대소문자를 가린다). */
	public static Optional<GoogleOAuthPurpose> ofVerify(String value) {
		if (LINK.name().equals(value)) {
			return Optional.of(LINK);
		}
		if (WITHDRAW.name().equals(value)) {
			return Optional.of(WITHDRAW);
		}
		return Optional.empty();
	}

}
