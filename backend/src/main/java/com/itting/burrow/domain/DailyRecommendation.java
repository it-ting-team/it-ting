package com.itting.burrow.domain;

import java.time.LocalDate;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "daily_recommendation")
public class DailyRecommendation extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "recommended_on", nullable = false, updatable = false)
	private LocalDate recommendedOn;

	protected DailyRecommendation() {
	}

	public Long getId() {
		return id;
	}

}
