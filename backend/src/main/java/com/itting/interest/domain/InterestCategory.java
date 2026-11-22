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
 * 관심사 대분류({@code interest_category}). 운영자가 DB에서 직접 관리한다.
 * 커뮤니티 글의 태그와 관심사 질문의 연결은 대분류를 쓴다.
 */
@Entity
@Table(name = "interest_category")
public class InterestCategory extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String name;

	/** 지운 시각. 비어 있으면 지우지 않은 대분류다. */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected InterestCategory() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
