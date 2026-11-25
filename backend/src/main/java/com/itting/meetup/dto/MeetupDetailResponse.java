package com.itting.meetup.dto;

import java.time.Instant;
import java.util.List;

import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.domain.MeetupViewerStatus;
import com.itting.meetup.domain.RecruitStatus;
import com.itting.user.dto.UserDisplayResponse;

public record MeetupDetailResponse(Long id, String title, MeetupCategory category, String description, String supplies,
		boolean isMessageEnabled, Instant startsAt, Instant endsAt, Instant recruitDeadlineAt, MeetupMode mode,
		String place, String placeDetail, String joinUrl, int capacity, long confirmedCount, RecruitStatus recruitStatus,
		UserDisplayResponse host, List<ParticipantResponse> participants, long sharedInterestParticipantCount,
		boolean isLiked, boolean hasVacancyAlert, MeetupViewerStatus myStatus, boolean canApply,
		boolean canCancelParticipation, boolean canEdit, boolean canCancel, boolean canDelete, Long chatRoomId) {

	public record ParticipantResponse(UserDisplayResponse user, boolean isHost, boolean isMe,
			boolean hasSharedInterest) {
	}

}
