package com.itting.global.error;

/**
 * 서비스에서 실패를 알릴 때 던진다. {@link GlobalExceptionHandler}가 응답 틀로 바꾼다.
 *
 * <pre>{@code
 * throw new BusinessException(MeetupErrorCode.MEETUP_NOT_FOUND);
 * throw new BusinessException(AuthErrorCode.ACCOUNT_SUSPENDED, new SuspensionDetail(reason, endsAt));
 * }</pre>
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	private final Object detail;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, null);
	}

	/**
	 * @param detail 화면에 보여 줄 값. 무엇을 담는지는 그 코드를 쓰는 API 문서에 적는다
	 */
	public BusinessException(ErrorCode errorCode, Object detail) {
		super(errorCode.code());
		this.errorCode = errorCode;
		this.detail = detail;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public Object getDetail() {
		return detail;
	}

}
