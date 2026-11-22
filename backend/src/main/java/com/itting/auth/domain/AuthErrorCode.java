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
	ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정입니다."),

	EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입한 이메일입니다."),

	MATTERMOST_ID_DUPLICATED(HttpStatus.CONFLICT, "다른 계정이 가입에 쓴 Mattermost ID입니다."),

	GOOGLE_ACCOUNT_DUPLICATED(HttpStatus.CONFLICT, "이미 가입한 Google 계정입니다."),

	VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않습니다."),

	/** 코드를 보낸 적이 없거나, 유효 시간이 지났거나, 너무 많이 틀려 지웠다. */
	VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료됐습니다. 코드를 다시 받아 주세요."),

	/** 메일 서버나 Mattermost 웹후크가 모두 실패해 코드를 보내지 못했다. 바로 다시 시도할 수 있다. */
	VERIFICATION_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "인증 코드를 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요."),

	/** 가입 진행 기록이 없거나 시간이 지났다. 가입을 처음부터 다시 한다. */
	SIGNUP_EXPIRED(HttpStatus.CONFLICT, "가입 시간이 지났습니다. 처음부터 다시 진행해 주세요.");

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
