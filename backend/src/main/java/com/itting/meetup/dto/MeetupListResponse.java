package com.itting.meetup.dto;

import java.time.Instant;
import java.util.List;

import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.domain.RecruitStatus;

public record MeetupListResponse(List<MeetupCardResponse> items, int page, int size, long totalItems, long totalPages) {

	public record MeetupCardResponse(Long id, String title, MeetupCategory category, Instant startsAt, Instant endsAt,
			MeetupMode mode, String place, int capacity, long confirmedCount, RecruitStatus recruitStatus) {
	}

}
