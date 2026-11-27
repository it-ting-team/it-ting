package com.itting.user.domain;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 탈퇴 기록({@code user_withdrawal}). 탈퇴한 계정과 사유, 시각({@code created_at})을 남긴다. 계정 하나에 하나이고 바꾸지 않는다.
 */
@Entity
@Table(name = "user_withdrawal")
public class UserWithdrawal extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	/** 고르지 않았으면 비어 있다. */
	@Enumerated(EnumType.STRING)
	@Column(updatable = false)
	private WithdrawalReason reason;

	protected UserWithdrawal() {
	}

	public static UserWithdrawal create(Long userId, WithdrawalReason reason) {
		UserWithdrawal withdrawal = new UserWithdrawal();
		withdrawal.userId = userId;
		withdrawal.reason = reason;
		return withdrawal;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public WithdrawalReason getReason() {
		return reason;
	}

}
