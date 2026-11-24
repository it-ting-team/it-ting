package com.itting.notice.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 공지 영역의 에러 코드.
 */
public enum NoticeErrorCode implements ErrorCode {

	/** 없는 공지, 삭제한 공지. */
	NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다.");

	private final HttpStatus status;

	private final String message;

	NoticeErrorCode(HttpStatus status, String message) {
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
