package com.itting.global.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 만든 시각(created_at)만 있는 테이블의 엔티티가 상속한다.
 * 한 번 쓰면 바뀌지 않는 테이블(버전, 메시지, 좋아요 등)이 해당한다. 기준은 docs/db/README.md의 공통 날짜 칼럼.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseCreatedTimeEntity {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Instant getCreatedAt() {
		return createdAt;
	}

}
