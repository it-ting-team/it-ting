package com.itting.meetup.dto;

import java.time.Instant;
import java.util.List;

import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.domain.MeetupViewerStatus;
import com.itting.meetup.domain.RecruitStatus;
import com.itting.meetup.dto.MeetupListResponse.MeetupCardResponse;

public record MyMeetupResponse(List<ItemResponse> items) {

	public record ItemResponse(Long id, String title, MeetupCategory category, Instant startsAt, Instant endsAt,
			MeetupMode mode, String place, int capacity, long confirmedCount, RecruitStatus recruitStatus,
			MeetupViewerStatus myStatus, boolean isLiked, boolean hasVacancyAlert, long pendingCount,
			boolean canEdit, Long chatRoomId, long unreadCount) {

		public ItemResponse(MeetupCardResponse card, MeetupViewerStatus myStatus, boolean isLiked,
				boolean hasVacancyAlert, long pendingCount, boolean canEdit, Long chatRoomId, long unreadCount) {
			this(card.id(), card.title(), card.category(), card.startsAt(), card.endsAt(), card.mode(), card.place(),
					card.capacity(), card.confirmedCount(), card.recruitStatus(), myStatus, isLiked,
					hasVacancyAlert, pendingCount, canEdit, chatRoomId, unreadCount);
		}
	}

}
