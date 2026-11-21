package com.itting.global.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * 만든 시각(created_at)과 바꾼 시각(updated_at)이 모두 있는 테이블의 엔티티가 상속한다.
 */
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedTimeEntity {

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
