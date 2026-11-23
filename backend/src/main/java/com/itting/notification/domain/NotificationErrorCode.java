package com.itting.notification.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 알림 영역의 에러 코드.
 */
public enum NotificationErrorCode implements ErrorCode {

	/** 없는 알림이거나, 내가 받은 알림이 아니거나, 30일이 지난 알림. */
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");

	private final HttpStatus status;

	private final String message;

	NotificationErrorCode(HttpStatus status, String message) {
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
