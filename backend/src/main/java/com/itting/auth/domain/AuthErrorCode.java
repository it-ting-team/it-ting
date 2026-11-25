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

	/** 이미 다른 Google 계정이 연결된 계정이 연동하려 했다. 확인 · 연동에서 쓴다. */
	GOOGLE_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 Google 계정이 연결돼 있습니다."),

	/** 탈퇴 전 확인에서 받은 Google 계정이 내 계정에 연결된 Google 계정과 다르다. 돌아가는 주소의 {@code error}로만 쓴다. */
	GOOGLE_ACCOUNT_MISMATCH(HttpStatus.CONFLICT, "연결된 Google 계정과 다른 계정입니다."),

	/** 설정에서 Google 확인을 취소했거나 마치지 못했다. 돌아가는 주소의 {@code error}로만 쓴다. */
	GOOGLE_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "Google 계정을 확인하지 못했습니다."),

	/** Google 확인을 받지 않았거나 받은 지 10분이 지났다. */
	GOOGLE_VERIFICATION_REQUIRED(HttpStatus.CONFLICT, "Google 계정 확인이 필요합니다."),

	/** 비밀번호가 없는 계정(Google로만 가입)이 연동을 해제하려 했다. 해제하면 로그인할 방법이 없어진다. */
	GOOGLE_UNLINK_NOT_ALLOWED(HttpStatus.CONFLICT, "비밀번호가 없는 계정은 Google 연동을 해제할 수 없습니다."),

	/** Google 로그인을 취소했거나 마치지 못했다. 로그인 화면 주소의 {@code error}로만 쓴다. */
	GOOGLE_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "Google 로그인을 마치지 못했습니다."),

	VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않습니다."),

	/** 코드를 보낸 적이 없거나, 유효 시간이 지났거나, 너무 많이 틀려 지웠다. */
	VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료됐습니다. 코드를 다시 받아 주세요."),

	/** 메일 서버나 Mattermost 웹후크가 모두 실패해 코드를 보내지 못했다. 바로 다시 시도할 수 있다. */
	VERIFICATION_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "인증 코드를 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요."),

	/** 30분이 지났거나, 이미 썼거나, 다시 보내서 지워졌거나, 없는 재설정 링크. */
	PASSWORD_RESET_LINK_INVALID(HttpStatus.NOT_FOUND, "만료됐거나 이미 쓴 링크입니다. 재설정 메일을 다시 받아 주세요."),

	/** 가입 진행 기록이 없거나 시간이 지났다. 가입을 처음부터 다시 한다. */
	SIGNUP_EXPIRED(HttpStatus.CONFLICT, "가입 시간이 지났습니다. 처음부터 다시 진행해 주세요."),

	/** 온보딩을 마치지 않은 계정이 "온보딩 중에도"가 아닌 API를 불렀다. */
	ONBOARDING_INCOMPLETE(HttpStatus.FORBIDDEN, "온보딩을 마친 뒤에 이용할 수 있습니다."),

	/** 새 학기의 캠퍼스 · 층을 고르지 않은 계정이 "새 학기를 고르기 전에도"가 아닌 API를 불렀다. */
	NEW_TERM_INCOMPLETE(HttpStatus.FORBIDDEN, "이번 학기의 캠퍼스와 층을 고른 뒤에 이용할 수 있습니다.");

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
