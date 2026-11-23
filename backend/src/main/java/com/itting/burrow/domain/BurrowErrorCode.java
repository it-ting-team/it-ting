package com.itting.burrow.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 땅굴 영역의 에러 코드.
 */
public enum BurrowErrorCode implements ErrorCode {

	/** 없는 땅굴. 주인이 탈퇴해 지워진 땅굴도 여기에 든다. */
	BURROW_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 땅굴입니다.");

	private final HttpStatus status;

	private final String message;

	BurrowErrorCode(HttpStatus status, String message) {
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
