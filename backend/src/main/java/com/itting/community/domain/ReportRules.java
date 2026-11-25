package com.itting.community.domain;

/**
 * 신고의 규칙({@code docs/api/08-community.md}의 신고).
 */
public final class ReportRules {

	/** 마지막 복구 뒤에 서로 다른 이만큼의 사람이 신고하면 글 · 댓글 · 방명록 글을 가린다. */
	public static final int HIDE_THRESHOLD = 3;

	/** 기타 사유의 최대 글자 수. */
	public static final int DETAIL_MAX_LENGTH = 100;

	private ReportRules() {
	}

}
