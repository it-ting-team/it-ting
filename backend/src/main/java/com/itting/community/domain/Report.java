package com.itting.community.domain;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 글 · 댓글 · 사용자 · 방명록 글에 접수된 신고({@code report}). 한 사람은 한 대상을 한 번만 신고한다.
 * 글과 댓글은 버전이 아니라 글 · 댓글 자체를 가리키므로, 내용을 고쳐도 같은 대상에 쌓인다.
 */
@Entity
@Table(name = "report")
public class Report extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reporter_id", nullable = false, updatable = false)
	private Long reporterId;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, updatable = false)
	private ReportTargetType targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private Long targetId;

	/** 신고 당시 보이던 버전의 번호. 글과 댓글에만 있다. */
	@Column(name = "target_version", updatable = false)
	private Integer targetVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private ReportReason reason;

	/** 기타 사유의 상세 내용. {@link ReportReason#ETC}에만 있다. */
	@Column(length = 100, updatable = false)
	private String detail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportStatus status;

	protected Report() {
	}

	/**
	 * 접수한 신고.
	 *
	 * @param targetVersion 글 · 댓글이면 지금 보이는 버전의 번호, 그 밖에는 null
	 * @param detail {@link ReportReason#ETC}면 상세 내용, 그 밖에는 null
	 */
	public static Report receive(Long reporterId, ReportTargetType targetType, Long targetId, Integer targetVersion,
			ReportReason reason, String detail) {
		Report report = new Report();
		report.reporterId = reporterId;
		report.targetType = targetType;
		report.targetId = targetId;
		report.targetVersion = targetVersion;
		report.reason = reason;
		report.detail = detail;
		report.status = ReportStatus.RECEIVED;
		return report;
	}

	public Long getId() {
		return id;
	}

	public Long getReporterId() {
		return reporterId;
	}

	public ReportTargetType getTargetType() {
		return targetType;
	}

	public Long getTargetId() {
		return targetId;
	}

	public Integer getTargetVersion() {
		return targetVersion;
	}

	public ReportReason getReason() {
		return reason;
	}

	public String getDetail() {
		return detail;
	}

	public ReportStatus getStatus() {
		return status;
	}

}
