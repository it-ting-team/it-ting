package com.itting.community.domain;

/**
 * 댓글의 입력 범위({@code docs/api/08-community.md}의 댓글 쓰기). DB 칼럼의 길이와 같다.
 */
public final class CommentRules {

	public static final int CONTENT_MAX = 300;

	/** 댓글 목록에서 한 번에 받을 댓글 수의 기본값과 가장 큰 값. 답글은 세지 않는다. */
	public static final int LIST_DEFAULT_SIZE = 10;

	public static final int LIST_MAX_SIZE = 100;

	private CommentRules() {
	}

}
