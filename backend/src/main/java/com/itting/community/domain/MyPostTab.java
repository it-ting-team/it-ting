package com.itting.community.domain;

/**
 * 내 활동의 탭({@code GET /api/v1/me/posts}의 {@code tab}).
 */
public enum MyPostTab {

	/** 내가 쓴 글 */
	WRITTEN,

	/** 댓글 단 글 */
	COMMENTED,

	/** 저장한 글 */
	BOOKMARKED

}
