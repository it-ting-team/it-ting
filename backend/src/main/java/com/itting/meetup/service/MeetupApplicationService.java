package com.itting.meetup.service;

import static com.itting.meetup.domain.MeetupApplicationStatus.CANCELED;
import static com.itting.meetup.domain.MeetupApplicationStatus.CONFIRMED;
import static com.itting.meetup.domain.MeetupApplicationStatus.KICKED;
import static com.itting.meetup.domain.MeetupApplicationStatus.PENDING;
import static com.itting.meetup.domain.MeetupApplicationStatus.REJECTED;
import static com.itting.meetup.domain.MeetupApplicationStatus.WITHDRAWN;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.interest.dto.InterestTagResponse;
import com.itting.interest.service.InterestTagService;
import com.itting.meetup.domain.Meetup;
import com.itting.meetup.domain.MeetupApplicationStatus;
import com.itting.meetup.domain.MeetupErrorCode;
import com.itting.meetup.domain.RecruitStatus;
import com.itting.meetup.dto.MeetupApplicationListResponse;
import com.itting.meetup.dto.MeetupApplicationListResponse.ItemResponse;
import com.itting.meetup.dto.MeetupApplicationResponse;
import com.itting.meetup.repository.MeetupParticipationRepository;
import com.itting.meetup.repository.MeetupParticipationRepository.Application;
import com.itting.meetup.repository.MeetupRepository;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.MeetupRequestCountReader;
import com.itting.notification.service.NotificationService;
import com.itting.user.domain.User;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.repository.UserRepository;
import com.itting.user.service.UserDisplayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class MeetupApplicationService implements MeetupRequestCountReader {

	private final MeetupRepository meetups;
	private final MeetupParticipationRepository applications;
	private final MeetupChatRooms chatRooms;
	private final NotificationService notifications;
	private final UserDisplayService displays;
	private final InterestTagService interests;
	private final UserRepository users;
	private final Clock clock;

	public MeetupApplicationService(MeetupRepository meetups, MeetupParticipationRepository applications,
			MeetupChatRooms chatRooms, NotificationService notifications, UserDisplayService displays,
			InterestTagService interests, UserRepository users, Clock clock) {
		this.meetups = meetups;
		this.applications = applications;
		this.chatRooms = chatRooms;
		this.notifications = notifications;
		this.displays = displays;
		this.interests = interests;
		this.users = users;
		this.clock = clock;
	}

	public MeetupApplicationResponse apply(Long meetupId, Long userId, String message) {
		// 탈퇴와 같은 순서(계정 → 모임)로 잠근다.
		lockActiveUser(userId);
		Meetup meetup = lockMeetup(meetupId);
		Instant now = clock.instant();
		if (meetup.getHostId().equals(userId)) {
			throw new BusinessException(MeetupErrorCode.OWN_MEETUP_NOT_ALLOWED);
		}
		Application application = applications.findApplication(meetupId, userId).orElse(null);
		if (application != null && application.status() != WITHDRAWN) {
			MeetupApplicationStatus status = effectiveStatus(application, meetup, now);
			throw new BusinessException(status == PENDING || status == CONFIRMED
					? MeetupErrorCode.MEETUP_ALREADY_APPLIED : MeetupErrorCode.MEETUP_REAPPLY_NOT_ALLOWED);
		}
		if (meetup.recruitStatus(now, confirmedCount(meetup)) != RecruitStatus.OPEN) {
			throw new BusinessException(MeetupErrorCode.MEETUP_NOT_RECRUITING);
		}
		String savedMessage = meetup.getDetails().isMessageEnabled() && message != null && !message.isEmpty()
				? message : null;
		Long applicationId;
		if (application == null) {
			applicationId = applications.insertApplication(meetupId, userId, savedMessage, now);
		}
		else {
			applicationId = application.id();
			applications.reopenApplication(applicationId, savedMessage, now);
		}
		notifications.notify(meetup.getHostId(), NotificationType.MEETUP_APPLICATION_RECEIVED, userId, meetupId);
		return new MeetupApplicationResponse(applicationId);
	}

	@Transactional(readOnly = true)
	public MeetupApplicationListResponse list(Long meetupId, Long userId) {
		Meetup meetup = meetups.findByIdAndDeletedAtIsNull(meetupId)
				.orElseThrow(() -> new BusinessException(MeetupErrorCode.MEETUP_NOT_FOUND));
		requireHost(meetup, userId);
		Instant now = clock.instant();
		boolean canManage = !meetup.isCanceled() && !meetup.isStarted(now);
		List<Application> rows = applications.findForManagement(meetupId, canManage && !meetup.isRecruitmentEnded(now));
		List<Long> userIds = rows.stream().map(Application::userId).toList();
		Map<Long, UserDisplayResponse> users = displays.getAll(userIds);
		Map<Long, List<InterestTagResponse>> sharedTags = interests.getSharedTags(userIds, userId);
		List<ItemResponse> items = rows.stream()
				.map(row -> new ItemResponse(row.id(), row.status(), users.get(row.userId()),
						sharedTags.getOrDefault(row.userId(), List.of()).size(), row.message()))
				.toList();
		long pendingCount = rows.stream().filter(row -> row.status() == PENDING).count();
		return new MeetupApplicationListResponse(meetupId, meetup.getDetails().title(), meetup.getDetails().capacity(),
				1L + rows.size() - pendingCount, pendingCount, canManage, items);
	}

	@Override
	@Transactional(readOnly = true)
	public long countPendingApplications(Long hostId) {
		return applications.countPendingForHost(hostId, clock.instant());
	}

	public void withdraw(Long meetupId, Long userId) {
		Meetup meetup = lockMeetup(meetupId);
		Instant now = clock.instant();
		Application application = findMine(meetupId, userId);
		if (application.status() == WITHDRAWN) {
			return;
		}
		if (application.status() == CONFIRMED) {
			throw new BusinessException(MeetupErrorCode.MEETUP_APPLICATION_ALREADY_CONFIRMED);
		}
		if (effectiveStatus(application, meetup, now) != PENDING || meetup.isCanceled()) {
			throw applicationNotFound();
		}
		applications.changeStatus(application.id(), WITHDRAWN, now);
	}

	public void cancelParticipation(Long meetupId, Long userId) {
		Meetup meetup = lockMeetup(meetupId);
		Instant now = clock.instant();
		Application application = findMine(meetupId, userId);
		if (application.status() == CANCELED) {
			return;
		}
		requireConfirmed(application);
		if (!meetup.canCancelParticipation(now)) {
			throw new BusinessException(MeetupErrorCode.MEETUP_PARTICIPATION_CANCEL_NOT_ALLOWED);
		}
		removeParticipant(meetup, application, CANCELED, now);
		notifications.notify(meetup.getHostId(), NotificationType.MEETUP_PARTICIPATION_CANCELED, userId, meetupId);
	}

	public void accept(Long applicationId, Long userId) {
		Meetup meetup = lockForApplication(applicationId, userId);
		Application application = findById(applicationId);
		Instant now = clock.instant();
		if (application.status() == CONFIRMED) {
			return;
		}
		if (effectiveStatus(application, meetup, now) != PENDING || meetup.isCanceled()) {
			throw applicationNotFound();
		}
		if (confirmedCount(meetup) >= meetup.getDetails().capacity()) {
			throw new BusinessException(MeetupErrorCode.MEETUP_FULL);
		}
		applications.changeStatus(applicationId, CONFIRMED, now);
		chatRooms.join(meetup.getId(), application.userId(), now);
		notifications.notify(application.userId(), NotificationType.MEETUP_APPLICATION_ACCEPTED, userId, meetup.getId());
	}

	public void reject(Long applicationId, Long userId) {
		Meetup meetup = lockForApplication(applicationId, userId);
		Application application = findById(applicationId);
		Instant now = clock.instant();
		MeetupApplicationStatus status = effectiveStatus(application, meetup, now);
		if (status == REJECTED) {
			return;
		}
		if (status != PENDING || meetup.isCanceled()) {
			throw applicationNotFound();
		}
		applications.changeStatus(applicationId, REJECTED, now);
		notifications.notify(application.userId(), NotificationType.MEETUP_APPLICATION_REJECTED, userId, meetup.getId());
	}

	public void kick(Long applicationId, Long userId) {
		Meetup meetup = lockForApplication(applicationId, userId);
		Application application = findById(applicationId);
		Instant now = clock.instant();
		if (application.status() == KICKED) {
			return;
		}
		requireConfirmed(application);
		if (meetup.isCanceled() || meetup.isStarted(now)) {
			throw new BusinessException(MeetupErrorCode.MEETUP_KICK_NOT_ALLOWED);
		}
		removeParticipant(meetup, application, KICKED, now);
		notifications.notify(application.userId(), NotificationType.MEETUP_KICKED, userId, meetup.getId());
	}

	private void removeParticipant(Meetup meetup, Application application, MeetupApplicationStatus status, Instant now) {
		Long meetupId = meetup.getId();
		boolean opensVacancy = !meetup.isRecruitmentEnded(now) && confirmedCount(meetup) == meetup.getDetails().capacity();
		applications.changeStatus(application.id(), status, now);
		chatRooms.leave(meetupId, application.userId(), now);
		if (opensVacancy) {
			notifications.notifyAll(applications.findVacancySubscribers(meetupId),
					NotificationType.MEETUP_VACANCY_OPENED, null, meetupId);
		}
	}

	private long confirmedCount(Meetup meetup) {
		return 1 + applications.countConfirmed(meetup.getId());
	}

	/**
	 * 내 계정 줄을 공유 잠금으로 잡고 아직 활성인지 본다. 트랜잭션의 첫 SQL로 부른다. 탈퇴({@code WithdrawalService})가 같은 줄을
	 * 먼저 잠그므로, 겹치면 탈퇴가 끝난 뒤 보고 거절하거나 탈퇴가 이 요청이 끝난 뒤 새 줄까지 정리한다.
	 */
	private void lockActiveUser(Long userId) {
		users.findByIdForShare(userId).filter(User::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
	}

	private Meetup lockMeetup(Long meetupId) {
		return meetups.findVisibleForUpdate(meetupId)
				.orElseThrow(() -> new BusinessException(MeetupErrorCode.MEETUP_NOT_FOUND));
	}

	private Meetup lockForApplication(Long applicationId, Long userId) {
		// 먼저 모임 ID만 찾고, 모임 행을 잠근 후 READ_COMMITTED 트랜잭션에서 현재 신청을 다시 읽는다.
		Long meetupId = findById(applicationId).meetupId();
		Meetup meetup = meetups.findVisibleForUpdate(meetupId)
				.orElseThrow(MeetupApplicationService::applicationNotFound);
		requireHost(meetup, userId);
		return meetup;
	}

	private Application findMine(Long meetupId, Long userId) {
		return applications.findApplication(meetupId, userId).orElseThrow(MeetupApplicationService::applicationNotFound);
	}

	private Application findById(Long applicationId) {
		return applications.findApplication(applicationId).orElseThrow(MeetupApplicationService::applicationNotFound);
	}

	private static void requireHost(Meetup meetup, Long userId) {
		if (!meetup.getHostId().equals(userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN);
		}
	}

	private static void requireConfirmed(Application application) {
		if (application.status() != CONFIRMED) {
			throw applicationNotFound();
		}
	}

	private static MeetupApplicationStatus effectiveStatus(Application application, Meetup meetup, Instant now) {
		return application.status() == PENDING && meetup.isRecruitmentEnded(now) ? REJECTED : application.status();
	}

	private static BusinessException applicationNotFound() {
		return new BusinessException(MeetupErrorCode.MEETUP_APPLICATION_NOT_FOUND);
	}

}
