package com.itting.meetup.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.itting.meetup.domain.Meetup;
import com.itting.meetup.repository.MeetupParticipationRepository;
import com.itting.meetup.repository.MeetupRepository;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MeetupReminderJob {

	private static final Duration CATCH_UP = Duration.ofMinutes(5);
	private static final List<Reminder> REMINDERS =
			List.of(new Reminder(Duration.ofHours(24), "24H"), new Reminder(Duration.ofHours(1), "1H"));

	private final MeetupRepository meetups;
	private final MeetupParticipationRepository participation;
	private final NotificationService notifications;
	private final TransactionTemplate transactions;
	private final Clock clock;

	public MeetupReminderJob(MeetupRepository meetups, MeetupParticipationRepository participation,
			NotificationService notifications, PlatformTransactionManager transactionManager, Clock clock) {
		this.meetups = meetups;
		this.participation = participation;
		this.notifications = notifications;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	public void sendDueReminders() {
		sendDueReminders(clock.instant());
	}

	void sendDueReminders(Instant now) {
		Instant earliest = now.minus(CATCH_UP);
		for (Reminder reminder : REMINDERS) {
			for (Long meetupId : meetups.findReminderCandidates(earliest.plus(reminder.lead()),
					now.plus(reminder.lead()), now)) {
				transactions.executeWithoutResult(status -> send(meetupId, reminder, now, earliest));
			}
		}
	}

	private void send(Long meetupId, Reminder reminder, Instant now, Instant earliest) {
		Meetup meetup = meetups.findVisibleForUpdate(meetupId).orElse(null);
		if (meetup == null || meetup.isCanceled() || meetup.isStarted(now)) {
			return;
		}
		Instant startsAt = meetup.getDetails().startsAt();
		Instant due = startsAt.minus(reminder.lead());
		// 후보를 찾은 뒤 시작 시각이 바뀌거나 해당 시점 이후에 모임을 만들었으면 건너뛴다.
		if (due.isAfter(now) || due.isBefore(earliest) || meetup.getCreatedAt().isAfter(due)) {
			return;
		}
		List<Long> receivers = new ArrayList<>(participation.findConfirmedBy(meetupId, due));
		receivers.add(meetup.getHostId());
		String eventKey = "MEETUP_START_REMINDER:%d:%d:%s".formatted(meetupId, startsAt.getEpochSecond(), reminder.key());
		for (Long receiverId : receivers) {
			notifications.notifyOnce(receiverId, NotificationType.MEETUP_START_REMINDER, null, meetupId, eventKey);
		}
	}

	private record Reminder(Duration lead, String key) {
	}

}
