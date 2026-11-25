package com.itting.meetup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.itting.meetup.domain.Meetup;
import com.itting.meetup.repository.MeetupParticipationRepository;
import com.itting.meetup.repository.MeetupRepository;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MeetupApplicationDeadlineJob {

	private static final Logger log = LoggerFactory.getLogger(MeetupApplicationDeadlineJob.class);
	private static final int BATCH_SIZE = 100;

	private final MeetupRepository meetups;
	private final MeetupParticipationRepository applications;
	private final NotificationService notifications;
	private final TransactionTemplate transactions;
	private final Clock clock;

	public MeetupApplicationDeadlineJob(MeetupRepository meetups, MeetupParticipationRepository applications,
			NotificationService notifications, PlatformTransactionManager transactionManager, Clock clock) {
		this.meetups = meetups;
		this.applications = applications;
		this.notifications = notifications;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	public void rejectExpiredApplications() {
		rejectExpiredApplications(clock.instant());
	}

	void rejectExpiredApplications(Instant now) {
		long afterId = 0;
		List<Long> candidates;
		do {
			candidates = applications.findExpiredWithPending(now, afterId, BATCH_SIZE);
			for (Long meetupId : candidates) {
				try {
					transactions.executeWithoutResult(status -> rejectForMeetup(meetupId, now));
				}
				catch (RuntimeException exception) {
					log.error("모임 {}의 신청 마감 처리에 실패했습니다.", meetupId, exception);
				}
			}
			if (!candidates.isEmpty()) {
				afterId = candidates.getLast();
			}
		}
		while (candidates.size() == BATCH_SIZE);
	}

	private void rejectForMeetup(Long meetupId, Instant now) {
		Meetup meetup = meetups.findVisibleForUpdate(meetupId).orElse(null);
		if (meetup == null || meetup.isCanceled() || !meetup.isRecruitmentEnded(now)) {
			return;
		}
		List<Long> pending = applications.findPendingUsers(meetupId);
		if (!pending.isEmpty()) {
			applications.rejectExpiredApplications(meetupId, now);
			notifications.notifyAll(pending, NotificationType.MEETUP_APPLICATION_REJECTED, null, meetupId);
		}
	}

}
