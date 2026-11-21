package com.itting.campus.domain;

import java.time.Instant;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 캠퍼스({@code campus}). 운영자가 DB에서 직접 관리한다.
 */
@Entity
@Table(name = "campus")
public class Campus extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String name;

	/** 캠퍼스 목록의 표시 순서. 작은 것이 먼저다. */
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	/** 지운 시각. 비어 있으면 지우지 않은 캠퍼스다. */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Campus() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
