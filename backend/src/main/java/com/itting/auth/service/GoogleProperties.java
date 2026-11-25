package com.itting.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OAuth 설정({@code itting.google}). 값은 {@code .env}나 환경 변수({@code ITTING_GOOGLE_*})로 넣는다.
 * OAuth 클라이언트는 Google Cloud 콘솔에서 사람이 만든다. 값이 비어 있어도 서버는 뜨고, Google이 거절하면
 * 로그인 · 확인이 실패로 끝난다(값을 따로 검사하지 않는다).
 *
 * @param clientId OAuth 클라이언트 ID. 비어 있으면 빈 값
 * @param clientSecret OAuth 클라이언트 보안 비밀. 비밀값이라 응답 · 로그에 남기지 않는다. 비어 있으면 빈 값
 * @param redirectUri Google이 브라우저를 돌려보낼 콜백의 전체 주소(예: {@code https://api.example.com/api/v1/oauth/google/callback}).
 *        Google Cloud 콘솔에 등록한 값과 한 글자도 다르면 안 된다. 비어 있으면 빈 값
 * @param authorizationUri Google 로그인 화면 주소. 비어 있으면 Google의 주소
 * @param tokenUri {@code code}를 액세스 토큰으로 바꾸는 주소. 비어 있으면 Google의 주소
 * @param userinfoUri 액세스 토큰으로 {@code sub}, {@code email}을 받는 주소. 비어 있으면 Google의 주소. 테스트는 이 셋을 가짜 서버로 바꾼다
 */
@ConfigurationProperties("itting.google")
public record GoogleProperties(
		String clientId,
		String clientSecret,
		String redirectUri,
		String authorizationUri,
		String tokenUri,
		String userinfoUri) {

	static final String DEFAULT_AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth";

	static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

	static final String DEFAULT_USERINFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";

	public GoogleProperties {
		clientId = clientId == null ? "" : clientId.strip();
		clientSecret = clientSecret == null ? "" : clientSecret.strip();
		redirectUri = redirectUri == null ? "" : redirectUri.strip();
		authorizationUri = blankTo(authorizationUri, DEFAULT_AUTHORIZATION_URI);
		tokenUri = blankTo(tokenUri, DEFAULT_TOKEN_URI);
		userinfoUri = blankTo(userinfoUri, DEFAULT_USERINFO_URI);
	}

	private static String blankTo(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.strip();
	}

}
