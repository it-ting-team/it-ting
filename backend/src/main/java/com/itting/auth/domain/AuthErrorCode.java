package com.itting.auth.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 로그인 · 회원가입 영역의 에러 코드({@code docs/api/02-auth.md}).
 */
public enum AuthErrorCode implements ErrorCode {

	/** 어느 쪽이 틀렸는지는 알리지 않는다. */
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않습니다."),

	/** {@code detail}에 정지 사유와 끝나는 시각을 담는다. */
	ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정입니다.");

	private final HttpStatus status;

	private final String message;

	AuthErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public HttpStatus status() {
		return status;
	}

	@Override
	public String message() {
		return message;
	}

}
