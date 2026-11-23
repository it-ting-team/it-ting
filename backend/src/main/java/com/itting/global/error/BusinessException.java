package com.itting.global.error;

import java.util.List;

import com.itting.global.response.ApiError;

/**
 * 서비스에서 실패를 알릴 때 던진다. {@link GlobalExceptionHandler}가 응답 틀로 바꾼다.
 *
 * <pre>{@code
 * throw new BusinessException(MeetupErrorCode.MEETUP_NOT_FOUND);
 * throw new BusinessException(AuthErrorCode.ACCOUNT_SUSPENDED, new SuspensionDetail(reason, endsAt));
 * throw BusinessException.invalidInput("generation", "고를 수 있는 기수가 아닙니다.");
 * }</pre>
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	private final Object detail;

	private final List<ApiError.FieldError> fields;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, null);
	}

	/**
	 * @param detail 화면에 보여 줄 값. 무엇을 담는지는 그 코드를 쓰는 API 문서에 적는다
	 */
	public BusinessException(ErrorCode errorCode, Object detail) {
		this(errorCode, detail, null);
	}

	private BusinessException(ErrorCode errorCode, Object detail, List<ApiError.FieldError> fields) {
		super(errorCode.code());
		this.errorCode = errorCode;
		this.detail = detail;
		this.fields = fields;
	}

	/**
	 * Bean Validation으로 볼 수 없는 입력 오류(DB나 서버 설정과 비교해야 아는 것)를 {@code 400 INVALID_INPUT}과
	 * {@code fields}로 알린다.
	 */
	public static BusinessException invalidInput(String field, String message) {
		return invalidInput(List.of(new ApiError.FieldError(field, message)));
	}

	/** 어긋난 입력이 여럿일 때. {@code fields}에 모두 담는다. */
	public static BusinessException invalidInput(List<ApiError.FieldError> fields) {
		return new BusinessException(CommonErrorCode.INVALID_INPUT, null, List.copyOf(fields));
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public Object getDetail() {
		return detail;
	}

	/** 입력 오류일 때만 어느 필드가 왜 틀렸는지. 그 밖에는 null. */
	public List<ApiError.FieldError> getFields() {
		return fields;
	}

}
