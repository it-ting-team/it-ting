package com.itting.term.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TermErrorCode implements ErrorCode {

	TERM_NOT_FOUND;

	@Override
	public HttpStatus status() {
		return HttpStatus.NOT_FOUND;
	}

	@Override
	public String message() {
		return "없거나 아직 열리지 않은 학기입니다.";
	}

}
