package com.itting.meetup.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MeetupErrorCode implements ErrorCode {
	MEETUP_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 모임입니다."),
	MEETUP_EDIT_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 모임을 수정할 수 없습니다."),
	MEETUP_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 모임을 취소할 수 없습니다."),
	MEETUP_DELETE_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 모임을 삭제할 수 없습니다."),
	OWN_MEETUP_NOT_ALLOWED(HttpStatus.FORBIDDEN, "내가 주최한 모임에는 신청할 수 없습니다."),
	MEETUP_ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 신청한 모임입니다."),
	MEETUP_REAPPLY_NOT_ALLOWED(HttpStatus.CONFLICT, "이 모임에는 다시 신청할 수 없습니다."),
	MEETUP_NOT_RECRUITING(HttpStatus.CONFLICT, "모집 중인 모임이 아닙니다."),
	MEETUP_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 신청입니다."),
	MEETUP_APPLICATION_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 참여가 확정된 신청입니다."),
	MEETUP_PARTICIPATION_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 참여를 취소할 수 없습니다."),
	MEETUP_FULL(HttpStatus.CONFLICT, "모임의 정원이 찼습니다."),
	MEETUP_KICK_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 참여자를 내보낼 수 없습니다.");

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
