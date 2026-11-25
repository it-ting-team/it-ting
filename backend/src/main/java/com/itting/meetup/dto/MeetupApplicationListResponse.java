package com.itting.meetup.dto;

import java.util.List;

import com.itting.meetup.domain.MeetupApplicationStatus;
import com.itting.user.dto.UserDisplayResponse;

public record MeetupApplicationListResponse(Long meetupId, String title, int capacity, long confirmedCount,
		long pendingCount, boolean canManage, List<ItemResponse> items) {

	public record ItemResponse(Long id, MeetupApplicationStatus status, UserDisplayResponse user,
			int sharedInterestCount, String message) {
	}

}
