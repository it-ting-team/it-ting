package com.itting.community.domain;

/**
 * 신고 사유({@code report.reason}). {@link #ETC}만 상세 내용({@code report.detail})을 적는다.
 */
public enum ReportReason {

	/** 스팸 · 홍보 */
	SPAM,

	/** 욕설 · 비하 · 괴롭힘 */
	ABUSE,

	/** 음란물 */
	OBSCENE,

	/** 기타 */
	ETC

}
