package com.itting.meetup.service;

import static com.itting.meetup.domain.MeetupApplicationStatus.CANCELED;
import static com.itting.meetup.domain.MeetupApplicationStatus.CONFIRMED;
import static com.itting.meetup.domain.MeetupApplicationStatus.PENDING;
import static com.itting.meetup.domain.MeetupApplicationStatus.WITHDRAWN;

import java.time.Instant;
import java.util.List;

import com.itting.meetup.domain.Meetup;
import com.itting.meetup.repository.MeetupLikeRepository;
import com.itting.meetup.repository.MeetupParticipationRepository;
import com.itting.meetup.repository.MeetupParticipationRepository.Application;
import com.itting.meetup.repository.MeetupRepository;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴의 모임 영역 정리({@code docs/api/10-settings.md}의 "탈퇴할 때 하는 일"). 탈퇴 트랜잭션 안에서만 부른다.
 *
 * <p>모임 대화방에서 빼는 일은 하지 않는다. 탈퇴는 들어 있는 모든 대화방에 {@code WITHDRAWN} 안내를 넣고 나가므로 채팅 영역의
 * 정리가 한다. 여기서 대화방을 나가면 {@code LEFT} 안내가 함께 들어간다.
 */
@Component
public class MeetupWithdrawalCleaner {

	private final MeetupRepository meetups;

	private final MeetupParticipationRepository applications;

	private final MeetupLikeRepository likes;

	private final NotificationService notifications;

	public MeetupWithdrawalCleaner(MeetupRepository meetups, MeetupParticipationRepository applications,
			MeetupLikeRepository likes, NotificationService notifications) {
		this.meetups = meetups;
		this.applications = applications;
		this.likes = likes;
		this.notifications = notifications;
	}

	/** 탈퇴를 막는, 내가 연 예정 모임. 시작이 가까운 것부터. */
	@Transactional(readOnly = true)
	public List<HostingMeetup> findHostingUpcoming(Long userId, Instant now) {
		return meetups.findHostingUpcoming(userId, now).stream()
				.map(meetup -> new HostingMeetup(meetup.getId(), meetup.getDetails().title(),
						meetup.getDetails().startsAt()))
				.toList();
	}

	/**
	 * 예정 모임의 내 신청을 정리하고 찜을 지운다.
	 *
	 * <ul>
	 * <li>기다리는 신청: {@code WITHDRAWN}. 알림은 만들지 않는다. 모집이 마감돼 곧 미승인으로 바뀔 신청도 같다(탈퇴한 사람에게
	 * 미승인 알림이 가지 않게).
	 * <li>확정된 참여: {@code CANCELED}. 시작 24시간 전이 지났어도 바꾼다. 주최자에게 참여 취소 알림을 만들고, 정원이 차 있었고
	 * 모집 중이면 빈자리 알림을 켠 사람에게 빈자리 알림을 만든다(참여 취소 API와 같은 조건).
	 * </ul>
	 * 모임은 id 순서로 하나씩 잠그고 신청을 다시 읽는다. 그 사이 모임이 취소 · 삭제됐거나 시작했으면 건너뛴다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void clean(Long userId, Instant now) {
		for (Long meetupId : applications.findUpcomingWithOpenApplication(userId, now)) {
			meetups.findVisibleForUpdate(meetupId)
					.filter(meetup -> !meetup.isCanceled() && !meetup.isStarted(now))
					.ifPresent(meetup -> applications.findApplication(meetupId, userId)
							.ifPresent(application -> close(meetup, application, now)));
		}
		likes.deleteAllByUser(userId);
	}

	private void close(Meetup meetup, Application application, Instant now) {
		if (application.status() == PENDING) {
			applications.changeStatus(application.id(), WITHDRAWN, now);
			return;
		}
		if (application.status() != CONFIRMED) {
			return;
		}
		Long meetupId = meetup.getId();
		long confirmedWithHost = 1 + applications.countConfirmed(meetupId);
		boolean opensVacancy = !meetup.isRecruitmentEnded(now) && confirmedWithHost == meetup.getDetails().capacity();
		applications.changeStatus(application.id(), CANCELED, now);
		notifications.notify(meetup.getHostId(), NotificationType.MEETUP_PARTICIPATION_CANCELED, application.userId(),
				meetupId);
		if (opensVacancy) {
			List<Long> subscribers = applications.findVacancySubscribers(meetupId).stream()
					.filter(subscriber -> !subscriber.equals(application.userId()))
					.toList();
			notifications.notifyAll(subscribers, NotificationType.MEETUP_VACANCY_OPENED, null, meetupId);
		}
	}

	/** 내가 연 예정 모임. */
	public record HostingMeetup(Long id, String title, Instant startsAt) {
	}

}
