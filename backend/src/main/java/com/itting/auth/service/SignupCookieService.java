package com.itting.auth.service;

import java.time.Duration;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * 가입 쿠키 {@code ITTING_SIGNUP}. 가입 진행 기록을 가리킨다. 프론트엔드 코드는 읽거나 다루지 않는다.
 * 세션 쿠키와 같은 보안 속성을 쓰고, 가입 API가 있는 {@code /api/v1}에만 실린다.
 */
@Service
public class SignupCookieService {

	public static final String COOKIE_NAME = "ITTING_SIGNUP";

	private static final String PATH = "/api/v1";

	private final VerificationProperties properties;

	public SignupCookieService(VerificationProperties properties) {
		this.properties = properties;
	}

	public void write(HttpServletResponse response, String token) {
		addCookie(response, token, properties.signupTtl());
	}

	/** 계정을 만든 뒤 쿠키를 지운다. */
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
