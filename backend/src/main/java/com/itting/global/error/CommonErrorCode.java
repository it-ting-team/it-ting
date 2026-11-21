package com.itting.global.error;

import java.util.Arrays;

import org.springframework.http.HttpStatus;

/**
 * 어느 영역에서나 쓰는 공통 에러 코드다({@code docs/api-convention.md}의 "공통 에러 코드").
 */
public enum CommonErrorCode implements ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "할 수 없는 요청입니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "없는 주소입니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "이 주소에서 허용하지 않는 메서드입니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;

	private final String message;

	CommonErrorCode(HttpStatus status, String message) {
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

	/**
	 * 상태 코드만 아는 실패(서블릿 오류 화면, 스프링이 던진 예외)를 공통 코드로 바꾼다.
	 * 표에 없는 4xx는 {@link #INVALID_INPUT}, 그 밖은 {@link #INTERNAL_ERROR}다.
	 */
	public static CommonErrorCode fromStatus(int status) {
		return Arrays.stream(values())
				.filter(code -> code.status.value() == status)
				.findFirst()
				.orElse(status >= 400 && status < 500 ? INVALID_INPUT : INTERNAL_ERROR);
	}

}
