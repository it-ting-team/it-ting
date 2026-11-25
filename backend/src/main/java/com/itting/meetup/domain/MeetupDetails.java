package com.itting.meetup.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 모임 생성과 수정이 함께 쓰는 값. */
@Embeddable
public record MeetupDetails(
		@Column(nullable = false, length = 30) String title,
		@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
		MeetupCategory category,
		@Column(columnDefinition = "TEXT") String description,
		@Column(nullable = false) int capacity,
		@Column(length = 100) String supplies,
		@Column(name = "accepts_message", nullable = false) boolean isMessageEnabled,
		@Column(name = "starts_at", nullable = false) Instant startsAt,
		@Column(name = "ends_at") Instant endsAt,
		@Enumerated(EnumType.STRING) @Column(nullable = false) MeetupMode mode,
		@Column(length = 100) String place,
		@Column(name = "place_detail", length = 100) String placeDetail,
		@Column(name = "join_url", length = 500) String joinUrl) {
}
