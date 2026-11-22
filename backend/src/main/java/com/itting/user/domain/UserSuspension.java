package com.itting.user.domain;

import java.time.Instant;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 계정 정지 기록({@code user_suspension}). 시작과 끝 시각 사이이고 해제되지 않았으면 정지 중이다.
 */
@Entity
@Table(name = "user_suspension")
public class UserSuspension extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** 정지 사유. 정지된 사용자의 로그인 안내에 보여 준다. */
	@Column(nullable = false, length = 200)
	private String reason;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Column(name = "imposed_by", nullable = false)
	private Long imposedBy;

	/** 관리자가 직접 해제한 시각. 비어 있으면 해제하지 않았다. */
	@Column(name = "lifted_at")
	private Instant liftedAt;

	@Column(name = "lifted_by")
	private Long liftedBy;

	@Column(name = "lift_reason", columnDefinition = "TEXT")
	private String liftReason;

	protected UserSuspension() {
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getReason() {
		return reason;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public Long getImposedBy() {
		return imposedBy;
	}

	public Instant getLiftedAt() {
		return liftedAt;
	}

	public Long getLiftedBy() {
		return liftedBy;
	}

	public String getLiftReason() {
		return liftReason;
	}

}
