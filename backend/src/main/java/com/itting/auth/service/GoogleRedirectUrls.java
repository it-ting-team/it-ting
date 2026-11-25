package com.itting.auth.service;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.global.config.FrontendProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google에 다녀온 브라우저를 보낼 프론트엔드 주소({@code docs/api/02-auth.md}의 "구현하며 정한 것"). 서버 설정
 * {@code itting.frontend-url} 뒤에 경로를 붙인다. 쿼리의 값은 모두 엄격하게 인코딩한다.
 *
 * <p>경로는 프론트엔드의 화면 주소와 같아야 한다({@code frontend/src/api/auth/google.ts}의 {@code GOOGLE_RETURN}).
 * 한쪽을 바꾸면 다른 쪽과 API 문서도 함께 바꾼다.
 */
@Component
class GoogleRedirectUrls {

	/** 로그인된 뒤의 화면. 프론트엔드가 여기서 {@code GET /api/v1/me}의 {@code nextStep}을 보고 다음 화면으로 넘긴다. */
	private static final String LOGGED_IN = "/auth/google";

	private static final String SIGNUP = "/signup/ssafy";

	private static final String LOGIN = "/login";

	private static final String SETTINGS = "/settings";

	private static final String SETTINGS_GOOGLE = "/settings/google";

	private static final String SETTINGS_WITHDRAW = "/settings/leave";

	private final FrontendProperties frontend;

	GoogleRedirectUrls(FrontendProperties frontend) {
		this.frontend = frontend;
	}

	/** 로그인됨. 프론트엔드가 {@code GET /api/v1/me}의 {@code nextStep}으로 갈 곳을 정한다. */
	URI loggedIn() {
		return build(LOGGED_IN);
	}

	/** 처음 쓰는 Google 계정. 회원가입 3(SSAFY 인증)을 진행 표시 없이 보여 준다. */
	URI signup() {
		return build(SIGNUP, "via", "google");
	}

	/** 로그인이 풀려 있음({@code verify}, 콜백). */
	URI login() {
		return build(LOGIN);
	}

	/** 설정의 첫 화면. {@code verify}의 {@code purpose}가 틀림. */
	URI settings() {
		return build(SETTINGS);
	}

	URI loginError(AuthErrorCode code) {
		return build(LOGIN, "error", code.code());
	}

	/** 정지된 계정. 이메일 로그인의 {@code error.detail}과 같은 이름({@code reason}, {@code endsAt})으로 전한다. */
	URI suspended(String reason, Instant endsAt) {
		String endsAtText = DateTimeFormatter.ISO_INSTANT.format(endsAt.truncatedTo(ChronoUnit.SECONDS));
		return build(LOGIN, "error", AuthErrorCode.ACCOUNT_SUSPENDED.code(), "reason", reason, "endsAt", endsAtText);
	}

	/** 확인됨. {@code LINK}면 Google 연동하기 화면에 {@code verified=true}, {@code WITHDRAW}면 회원 탈퇴 화면. */
	URI verified(GoogleOAuthPurpose purpose) {
		return purpose == GoogleOAuthPurpose.LINK
				? build(SETTINGS_GOOGLE, "verified", "true")
				: build(SETTINGS_WITHDRAW);
	}

	/** 확인하지 못함. 시작한 화면에 {@code error=<code>}를 붙인다. */
	URI verifyError(GoogleOAuthPurpose purpose, AuthErrorCode code) {
		return build(purpose == GoogleOAuthPurpose.LINK ? SETTINGS_GOOGLE : SETTINGS_WITHDRAW, "error", code.code());
	}

	/** 목적에 맞는 실패 주소. 로그인이면 로그인 화면, 설정에서 시작한 확인이면 그 화면. */
	URI failure(GoogleOAuthPurpose purpose) {
		return purpose == GoogleOAuthPurpose.LOGIN
				? loginError(AuthErrorCode.GOOGLE_LOGIN_FAILED)
				: verifyError(purpose, AuthErrorCode.GOOGLE_VERIFICATION_FAILED);
	}

	/**
	 * @param query 쿼리 이름과 값을 번갈아 적는다
	 */
	private URI build(String path, String... query) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontend.frontendUrl()).path(path);
		Object[] values = new Object[query.length / 2];
		for (int i = 0; i < query.length; i += 2) {
			builder.queryParam(query[i], "{" + query[i] + "}");
			values[i / 2] = query[i + 1];
		}
		return builder.encode().buildAndExpand(values).toUri();
	}

}
