package com.itting.meetup.dto;

import java.time.Instant;
import java.util.List;

import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.domain.MeetupViewerStatus;
import com.itting.meetup.domain.RecruitStatus;
import com.itting.meetup.dto.MeetupListResponse.MeetupCardResponse;

public record MyMeetupSummaryResponse(CountsResponse counts, List<UpcomingItemResponse> upcomingItems) {

	public record CountsResponse(long upcoming, long pending, long hosting, long liked, long past) {
	}

	public record UpcomingItemResponse(Long id, String title, MeetupCategory category, Instant startsAt, Instant endsAt,
			MeetupMode mode, String place, int capacity, long confirmedCount, RecruitStatus recruitStatus,
			MeetupViewerStatus myStatus) {

		public UpcomingItemResponse(MeetupCardResponse card, MeetupViewerStatus myStatus) {
			this(card.id(), card.title(), card.category(), card.startsAt(), card.endsAt(), card.mode(), card.place(),
					card.capacity(), card.confirmedCount(), card.recruitStatus(), myStatus);
		}
	}

}
