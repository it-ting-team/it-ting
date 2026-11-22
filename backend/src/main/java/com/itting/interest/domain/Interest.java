package com.itting.interest.domain;

import java.time.Instant;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 세부 관심사({@code interest}). 사람이 고르는 관심사, 땅굴 가구, 겹치는 관심사는 세부 관심사로 센다.
 * 이름은 대분류가 달라도 겹치지 않는다.
 */
@Entity
@Table(name = "interest")
public class Interest extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "interest_category_id", nullable = false)
	private Long interestCategoryId;

	@Column(nullable = false, length = 30)
	private String name;

	/** 지운 시각. 비어 있으면 지우지 않은 관심사다. */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Interest() {
	}

	public Long getId() {
		return id;
	}

	public Long getInterestCategoryId() {
		return interestCategoryId;
	}

	public String getName() {
		return name;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
