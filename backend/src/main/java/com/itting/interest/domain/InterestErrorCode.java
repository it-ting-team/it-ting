package com.itting.interest.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 관심사 영역의 에러 코드. 관심사를 고르는 API(온보딩 확정, 내 정보 수정)가 함께 쓴다.
 */
public enum InterestErrorCode implements ErrorCode {

	/** 없거나 지운 세부 관심사. */
	INTEREST_NOT_FOUND(HttpStatus.NOT_FOUND, "없거나 지운 관심사입니다.");

	private final HttpStatus status;

	private final String message;

	InterestErrorCode(HttpStatus status, String message) {
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
