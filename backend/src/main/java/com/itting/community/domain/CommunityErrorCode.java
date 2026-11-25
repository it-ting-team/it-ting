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
	 * 없거나 삭제된 댓글. 답하려는 댓글이 이 글에 없거나 가려진 경우, 가려진 댓글이나 가려진 글의 댓글에 좋아요를 누르는 경우에도
	 * 쓴다(KAN-52).
	 */
	COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "없거나 삭제된 댓글입니다."),

	/** 신고로 가려진 댓글이나, 신고로 가려진 글의 댓글은 고칠 수 없다. */
	COMMENT_EDIT_NOT_ALLOWED(HttpStatus.CONFLICT, "신고로 가려진 댓글은 고칠 수 없습니다."),

	/**
	 * 있는 모임이지만 그 카테고리의 글에는 연결할 수 없다. 모임 홍보인데 내가 연 모집 중인 모임이 아니거나, 모임 후기인데 내가
	 * 열었거나 참여한 지난 모임이 아니거나, 자유인데 내가 열었거나 참여한 모임이 아니거나, 취소된 모임일 때. 없거나 삭제된
	 * 모임은 모임 영역의 {@code MeetupErrorCode.MEETUP_NOT_FOUND}로 답한다.
	 */
	MEETUP_NOT_LINKABLE(HttpStatus.CONFLICT, "이 글에 연결할 수 없는 모임입니다."),

	/**
	 * 신고할 대상이 없음. 없거나, 삭제됐거나, 이미 가려진 대상과 탈퇴한 사용자, 가려지거나 삭제된 글의 댓글이다.
	 */
	REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "신고할 대상을 찾을 수 없습니다."),

	/** 내 글 · 내 댓글 · 나 자신 · 내가 쓴 방명록 글은 신고할 수 없다. */
	OWN_TARGET_NOT_ALLOWED(HttpStatus.FORBIDDEN, "내 글이나 나 자신은 신고할 수 없습니다."),

	/** 같은 대상은 한 번만 신고할 수 있다. 관리자가 복구한 뒤에도 다시 신고할 수 없다. */
	ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 대상입니다.");

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
