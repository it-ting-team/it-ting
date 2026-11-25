package com.itting.auth.service;

/**
 * Google에서 계정을 받아 오지 못했다(통신 오류, 거절, 모르는 응답). 원인의 내용(응답 본문, 토큰)은 담지 않는다.
 * 호출한 쪽이 로그인 · 확인 실패로 바꿔 브라우저를 돌려보낸다.
 */
public class GoogleClientException extends RuntimeException {

	public GoogleClientException(String message) {
		super(message);
	}

	public GoogleClientException(String message, Throwable cause) {
		super(message, cause);
	}

}
