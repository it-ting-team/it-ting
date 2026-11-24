package com.itting.burrow.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "daily_recommendation_member")
@IdClass(DailyRecommendationMember.Key.class)
public class DailyRecommendationMember {

	@Id
	@Column(name = "daily_recommendation_id")
	private Long dailyRecommendationId;

	@Id
	@Column(name = "sort_order")
	private byte sortOrder;

	@Column(name = "target_user_id", nullable = false, updatable = false)
	private Long targetUserId;

	protected DailyRecommendationMember() {
	}

	public DailyRecommendationMember(Long dailyRecommendationId, int sortOrder, Long targetUserId) {
		this.dailyRecommendationId = dailyRecommendationId;
		this.sortOrder = (byte) sortOrder;
		this.targetUserId = targetUserId;
	}

	public record Key(Long dailyRecommendationId, byte sortOrder) implements Serializable {
	}

}
