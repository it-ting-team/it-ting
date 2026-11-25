package com.itting.meetup.service;

import java.time.Clock;

import com.itting.global.error.BusinessException;
import com.itting.meetup.domain.MeetupErrorCode;
import com.itting.meetup.repository.MeetupLikeRepository;
import com.itting.meetup.repository.MeetupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MeetupLikeService {

	private final MeetupRepository meetups;
	private final MeetupLikeRepository likes;
	private final Clock clock;

	public MeetupLikeService(MeetupRepository meetups, MeetupLikeRepository likes, Clock clock) {
		this.meetups = meetups;
		this.likes = likes;
		this.clock = clock;
	}

	public void like(Long meetupId, Long userId) {
		lockVisibleMeetup(meetupId);
		likes.insertIfAbsent(meetupId, userId, clock.instant());
	}

	public void unlike(Long meetupId, Long userId) {
		likes.delete(meetupId, userId);
	}

	public void enableAlert(Long meetupId, Long userId) {
		lockVisibleMeetup(meetupId);
		boolean enabled = likes.findAlertForUpdate(meetupId, userId)
				.orElseThrow(() -> new BusinessException(MeetupErrorCode.MEETUP_NOT_LIKED));
		if (!enabled) {
			likes.setAlert(meetupId, userId, true, clock.instant());
		}
	}

	public void disableAlert(Long meetupId, Long userId) {
		likes.setAlert(meetupId, userId, false, clock.instant());
	}

	private void lockVisibleMeetup(Long meetupId) {
		meetups.findVisibleForUpdate(meetupId)
				.orElseThrow(() -> new BusinessException(MeetupErrorCode.MEETUP_NOT_FOUND));
	}

}
