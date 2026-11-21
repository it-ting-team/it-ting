package com.itting.support;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 공통 날짜 칼럼과 DB 설정을 검증하려고 테스트에서만 쓰는 엔티티.
 * 테이블은 src/test/resources/db/migration의 테스트 전용 마이그레이션이 만든다.
 */
@Entity
@Table(name = "audit_sample")
public class AuditSample extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	protected AuditSample() {
	}

	public AuditSample(String name) {
		this.name = name;
	}

	public void rename(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

}
