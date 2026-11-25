package com.itting.community.domain;

/**
 * 신고 대상의 종류({@code report.target_type}). {@code target_id}가 가리키는 테이블이 종류마다 다르다.
 */
public enum ReportTargetType {

	/** 글. {@code post.id} */
	POST,

	/** 댓글이나 답글. {@code comment.id} */
	COMMENT,

	/** 사용자. {@code user.id}. 신고가 쌓여도 자동으로 가리지 않고 관리자가 본다. */
	MEMBER,

	/** 방명록 글. {@code guestbook_entry.id} */
	GUESTBOOK

}
