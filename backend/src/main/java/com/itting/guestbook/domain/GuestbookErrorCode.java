package com.itting.guestbook.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 방명록 영역의 에러 코드.
 */
public enum GuestbookErrorCode implements ErrorCode {

	/** 없는 글, 이미 지워진 글, 신고로 가려진 글. */
	GUESTBOOK_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 방명록 글입니다.");

	private final HttpStatus status;

	private final String message;

	GuestbookErrorCode(HttpStatus status, String message) {
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
