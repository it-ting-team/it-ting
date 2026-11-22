package com.itting.term.domain;

import java.time.Instant;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 학기({@code term}). 연도와 상 · 하반기로 나눈다. 운영자가 DB에서 직접 관리한다.
 * 이번 학기는 열린 학기 가운데 {@code opened_at}이 가장 최근인 것이다.
 */
@Entity
@Table(name = "term")
public class Term extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private short year;

	/** 1은 상반기, 2는 하반기. */
	@Column(nullable = false)
	private byte half;

	/** 들판을 연 시각. 이 시각이 지나야 학기 목록에 나온다. */
	@Column(name = "opened_at", nullable = false)
	private Instant openedAt;

	protected Term() {
	}

	public Long getId() {
		return id;
	}

	public int getYear() {
		return year;
	}

	public int getHalf() {
		return half;
	}

	public Instant getOpenedAt() {
		return openedAt;
	}

}
