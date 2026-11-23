package com.itting.community.domain;

/**
 * 글의 입력 범위({@code docs/api/08-community.md}의 글쓰기). DB 칼럼의 길이와 같다.
 */
public final class PostRules {

	public static final int TITLE_MAX = 50;

	public static final int CONTENT_MAX = 1000;

	public static final int INTEREST_MAX = 5;

	public static final int IMAGE_MAX = 3;

	/** 글 카드의 본문 미리보기 길이. */
	public static final int PREVIEW_LENGTH = 100;

	private PostRules() {
	}

}
