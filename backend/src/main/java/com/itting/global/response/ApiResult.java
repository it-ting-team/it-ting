package com.itting.global.response;

/**
 * 모든 API 응답 본문의 틀이다({@code docs/api-convention.md}의 "응답 틀").
 * 성공은 {@link #ok(Object)}로 만들고, 실패는 직접 만들지 않고 예외를 던진다.
 *
 * @param success 2xx일 때만 true
 * @param data 성공했을 때 돌려줄 값. 없거나 실패하면 null
 * @param error 실패했을 때의 내용. 성공하면 null
 */
public record ApiResult<T>(boolean success, T data, ApiError error) {

	public static <T> ApiResult<T> ok(T data) {
		return new ApiResult<>(true, data, null);
	}

	public static ApiResult<Void> ok() {
		return new ApiResult<>(true, null, null);
	}

	public static ApiResult<Void> fail(ApiError error) {
		return new ApiResult<>(false, null, error);
	}

}
