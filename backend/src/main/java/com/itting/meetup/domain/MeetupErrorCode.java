package com.itting.meetup.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MeetupErrorCode implements ErrorCode {
	MEETUP_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 모임입니다."),
	MEETUP_EDIT_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 모임을 수정할 수 없습니다."),
	MEETUP_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 모임을 취소할 수 없습니다."),
	MEETUP_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 모임을 삭제할 수 없습니다.");

	private final HttpStatus status;
	private final String message;

	MeetupErrorCode(HttpStatus status, String message) {
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
