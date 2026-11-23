package com.itting.question.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 자기소개 질문 영역의 에러 코드. 답변을 저장하는 API(온보딩 확정, 내 정보 수정)가 함께 쓴다.
 */
public enum IntroQuestionErrorCode implements ErrorCode {

	INTRO_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 자기소개 질문입니다.");

	private final HttpStatus status;

	private final String message;

	IntroQuestionErrorCode(HttpStatus status, String message) {
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
