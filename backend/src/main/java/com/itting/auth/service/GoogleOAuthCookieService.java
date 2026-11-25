package com.itting.auth.service;

import java.time.Duration;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Google 시도 쿠키 {@code ITTING_OAUTH}. 시작한 브라우저를 {@code state}로 묶는다. 콜백은 쿠키의 값과 Google이 돌려준
 * {@code state}가 같을 때만 받아들인다. 그래서 남이 시작한 로그인 주소를 내 브라우저가 열어도 남의 계정으로 로그인되지 않는다
 * (로그인 CSRF). 프론트엔드 코드는 읽거나 다루지 않는다.
 *
 * <p>Google에서 돌아오는 요청은 다른 사이트에서 온 최상위 이동이라 {@code SameSite=Lax}여야 쿠키가 실린다.
 * {@code Path}는 시작 · 콜백 주소가 있는 {@code /api/v1/oauth/google}라서 다른 API에는 실리지 않는다.
 */
@Service
public class GoogleOAuthCookieService {

	public static final String COOKIE_NAME = "ITTING_OAUTH";

	private static final String PATH = "/api/v1/oauth/google";

	public void write(HttpServletResponse response, String state, Duration ttl) {
		addCookie(response, state, ttl);
	}

	/** 콜백에서 쿠키를 한 번 쓴 뒤 지운다. */
	public void expire(HttpServletResponse response) {
		addCookie(response, "", Duration.ZERO);
	}

	private static void addCookie(HttpServletResponse response, String value, Duration maxAge) {
		ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path(PATH)
				.maxAge(maxAge)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

}
