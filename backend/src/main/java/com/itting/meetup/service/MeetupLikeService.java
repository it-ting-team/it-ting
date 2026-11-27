package com.itting.meetup.service;

import java.time.Clock;

import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.meetup.domain.MeetupErrorCode;
import com.itting.meetup.repository.MeetupLikeRepository;
import com.itting.meetup.repository.MeetupRepository;
import com.itting.user.domain.User;
import com.itting.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MeetupLikeService {

	private final MeetupRepository meetups;
	private final MeetupLikeRepository likes;
	private final UserRepository users;
	private final Clock clock;

	public MeetupLikeService(MeetupRepository meetups, MeetupLikeRepository likes, UserRepository users, Clock clock) {
		this.meetups = meetups;
		this.likes = likes;
		this.users = users;
		this.clock = clock;
	}

	public void like(Long meetupId, Long userId) {
		lockActiveUser(userId);
		lockVisibleMeetup(meetupId);
		likes.insertIfAbsent(meetupId, userId, clock.instant());
	}

	public void unlike(Long meetupId, Long userId) {
		likes.delete(meetupId, userId);
	}

	public void enableAlert(Long meetupId, Long userId) {
		lockActiveUser(userId);
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

	/**
	 * 내 계정 줄을 공유 잠금으로 잡고 아직 활성인지 본다. 트랜잭션의 첫 SQL로 부른다. 탈퇴({@code WithdrawalService})가 같은 줄을
	 * 먼저 잠그므로, 겹치면 탈퇴가 끝난 뒤 보고 거절하거나 탈퇴가 이 요청이 끝난 뒤 새 줄까지 정리한다.
	 */
	private void lockActiveUser(Long userId) {
		users.findByIdForShare(userId).filter(User::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
	}

	private void lockVisibleMeetup(Long meetupId) {
		meetups.findVisibleForUpdate(meetupId)
				.orElseThrow(() -> new BusinessException(MeetupErrorCode.MEETUP_NOT_FOUND));
	}

}
