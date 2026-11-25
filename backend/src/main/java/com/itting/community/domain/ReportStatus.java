package com.itting.community.domain;

/**
 * 신고의 처리 상태({@code report.status}). 접수하면 {@link #RECEIVED}이고, 관리자가 종결하면 {@link #RESOLVED}가 된다.
 */
public enum ReportStatus {
	RECEIVED,
	RESOLVED
}
