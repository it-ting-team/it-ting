package com.itting.user.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 프로필 영역의 에러 코드. 닉네임을 확인하거나 저장하는 API(닉네임 확인, 온보딩 확정, 내 정보 수정)가 함께 쓴다.
 */
public enum UserErrorCode implements ErrorCode {

	/** 다른 사람이 쓰고 있거나 맡아 둔 닉네임. 영어 대소문자만 다른 닉네임도 같은 것으로 본다. */
	NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");

	private final HttpStatus status;

	private final String message;

	UserErrorCode(HttpStatus status, String message) {
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
