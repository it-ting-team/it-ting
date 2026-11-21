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
 * 캠퍼스마다 고를 수 있는 층({@code campus_floor}). 다른 API의 {@code campusFloorId}가 이 id다.
 */
@Entity
@Table(name = "campus_floor")
public class CampusFloor extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(nullable = false)
	private int floor;

	/** 지운 시각. 비어 있으면 지우지 않은 층이다. */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected CampusFloor() {
	}

	public Long getId() {
		return id;
	}

	public Long getCampusId() {
		return campusId;
	}

	public int getFloor() {
		return floor;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
