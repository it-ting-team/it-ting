package com.itting.interest.domain;

import java.io.Serializable;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 사람이 고른 세부 관심사와 고른 순서({@code user_interest}). 한 사람은 3 ~ 10개를 고르고,
 * {@code sort_order}가 1인 것이 대표 관심사다. 저장은 03 온보딩 · 04 내 정보 수정이 한다.
 */
@Entity
@Table(name = "user_interest")
@IdClass(UserInterest.Key.class)
public class UserInterest extends BaseTimeEntity {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "interest_id")
	private Long interestId;

	@Column(name = "sort_order", nullable = false)
	private byte sortOrder;

	protected UserInterest() {
	}

	/**
	 * @param sortOrder 고른 순서. 1 ~ 10이고 1이 대표 관심사다
	 */
	public static UserInterest create(Long userId, Long interestId, int sortOrder) {
		UserInterest userInterest = new UserInterest();
		userInterest.userId = userId;
		userInterest.interestId = interestId;
		userInterest.sortOrder = (byte) sortOrder;
		return userInterest;
	}

	/** 내 정보 수정에서 관심사의 순서를 바꾼다. 1이 대표 관심사다. */
	public void changeSortOrder(int sortOrder) {
		this.sortOrder = (byte) sortOrder;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getInterestId() {
		return interestId;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	/** 복합 기본키 {@code (user_id, interest_id)}. */
	public record Key(Long userId, Long interestId) implements Serializable {
	}

}
