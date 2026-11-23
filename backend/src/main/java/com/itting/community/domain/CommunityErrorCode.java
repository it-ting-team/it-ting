package com.itting.community.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 커뮤니티 영역의 에러 코드({@code docs/api/08-community.md}).
 */
public enum CommunityErrorCode implements ErrorCode {

	/** 없는 글이거나 삭제된 글. 둘을 구분하지 않는다. */
	POST_NOT_FOUND(HttpStatus.NOT_FOUND, "없거나 삭제된 글입니다."),

	/** 신고로 가려진 글. 작성자에게는 쓰지 않는다(조회수 올리기는 작성자에게도 쓴다). */
	POST_HIDDEN(HttpStatus.NOT_FOUND, "신고로 가려진 글입니다."),

	/** 신고로 가려진 글은 고칠 수 없다. */
	POST_EDIT_NOT_ALLOWED(HttpStatus.CONFLICT, "신고로 가려진 글은 고칠 수 없습니다."),

	/**
	 * 글에 연결하려는 모임이 없거나 삭제됨. 모임 테이블이 생기기 전이라 지금은 어떤 모임 id에도 이 코드로 답한다.
	 * 08 커뮤니티 ③(KAN-53)에서 모임 영역의 코드로 바꾼다.
	 */
	MEETUP_NOT_FOUND(HttpStatus.NOT_FOUND, "없거나 삭제된 모임입니다.");

	private final HttpStatus status;

	private final String message;

	CommunityErrorCode(HttpStatus status, String message) {
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
