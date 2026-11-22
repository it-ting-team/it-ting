package com.itting.campus.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 캠퍼스 영역의 에러 코드. 캠퍼스-층을 고르는 여러 API(가입, 내 정보 수정, 새 학기 이사)가 함께 쓴다.
 */
public enum CampusErrorCode implements ErrorCode {

	/** 없거나 지운 캠퍼스-층. */
	CAMPUS_FLOOR_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 캠퍼스-층입니다.");

	private final HttpStatus status;

	private final String message;

	CampusErrorCode(HttpStatus status, String message) {
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
