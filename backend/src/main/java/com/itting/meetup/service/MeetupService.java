package com.itting.meetup.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.global.response.ApiError.FieldError;
import com.itting.meetup.domain.Meetup;
import com.itting.meetup.domain.MeetupDetails;
import com.itting.meetup.domain.MeetupErrorCode;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.dto.MeetupCreateRequest;
import com.itting.meetup.dto.MeetupCreateResponse;
import com.itting.meetup.dto.MeetupUpdateRequest;
import com.itting.meetup.repository.MeetupParticipationRepository;
import com.itting.meetup.repository.MeetupRepository;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetupService {

	private final MeetupRepository meetups;
	private final MeetupParticipationRepository participation;
	private final MeetupChatRooms chatRooms;
	private final NotificationService notifications;
	private final Validator validator;
	private final Clock clock;

	public MeetupService(MeetupRepository meetups, MeetupParticipationRepository participation,
			MeetupChatRooms chatRooms, NotificationService notifications, Validator validator, Clock clock) {
		this.meetups = meetups;
		this.participation = participation;
		this.chatRooms = chatRooms;
		this.notifications = notifications;
		this.validator = validator;
		this.clock = clock;
	}

	@Transactional
	public MeetupCreateResponse create(Long hostId, MeetupCreateRequest request) {
		Instant now = clock.instant();
		MeetupDetails details = request.details();
		Instant deadline = request.recruitDeadlineAt();
		if (deadline != null) {
			deadline = deadline.truncatedTo(ChronoUnit.MICROS);
		}
		List<FieldError> errors = new ArrayList<>();
		validateDetails(details, now, errors);
		if (deadline != null && (!deadline.isAfter(now) || deadline.isAfter(details.startsAt()))) {
			errors.add(new FieldError("recruitDeadlineAt", "지금보다 뒤이며 시작 시각 이하여야 합니다."));
		}
		rejectErrors(errors);
		Meetup meetup = meetups.save(new Meetup(hostId, details, deadline));
		Long roomId = chatRooms.create(meetup.getId(), hostId, now);
		return new MeetupCreateResponse(meetup.getId(), roomId);
	}

	@Transactional
	public void update(Long meetupId, Long userId, MeetupUpdateRequest request) {
		Meetup meetup = lockForHost(meetupId, userId);
		Instant now = clock.instant();
		if (!meetup.canEdit(now)) {
			throw new BusinessException(MeetupErrorCode.MEETUP_EDIT_NOT_ALLOWED);
		}
		List<FieldError> errors = new ArrayList<>();
		validator.validate(request).forEach(error ->
				errors.add(new FieldError(error.getPropertyPath().toString(), error.getMessage())));
		rejectErrors(errors);
		MeetupDetails before = meetup.getDetails();
		MeetupDetails details = request.details();
		List<Long> confirmed = participation.findConfirmedUsers(meetupId);
		long confirmedCount = 1L + confirmed.size();
		validateDetails(details, now, errors);
		if (meetup.getRecruitDeadlineAt() != null && details.startsAt().isBefore(meetup.getRecruitDeadlineAt())) {
			errors.add(new FieldError("startsAt", "모집 마감 시각보다 앞설 수 없습니다."));
		}
		if (details.capacity() < confirmedCount) {
			errors.add(new FieldError("capacity", "확정 인원 이상이어야 합니다."));
		}
		rejectErrors(errors);
		if (before.equals(details)) {
			return;
		}
		meetup.update(details);
		notifications.notifyAll(recipients(meetupId, confirmed), NotificationType.MEETUP_UPDATED, userId, meetupId);
		if (before.capacity() == confirmedCount && details.capacity() > confirmedCount && !meetup.isRecruitmentEnded(now)) {
			notifications.notifyAll(participation.findVacancySubscribers(meetupId),
					NotificationType.MEETUP_VACANCY_OPENED, null, meetupId);
		}
	}

	@Transactional
	public void cancel(Long meetupId, Long userId) {
		Meetup meetup = lockForHost(meetupId, userId);
		if (meetup.isCanceled()) {
			return;
		}
		Instant now = clock.instant();
		List<Long> confirmed = participation.findConfirmedUsers(meetupId);
		if (meetup.isStarted(now) || confirmed.isEmpty()) {
			throw new BusinessException(MeetupErrorCode.MEETUP_CANCEL_NOT_ALLOWED);
		}
		List<Long> receivers = recipients(meetupId, confirmed);
		meetup.cancel(now);
		participation.closePendingOnCancellation(meetupId, now);
		notifications.notifyAll(receivers, NotificationType.MEETUP_CANCELED, userId, meetupId);
	}

	@Transactional
	public void delete(Long meetupId, Long userId) {
		Meetup meetup = lockForHost(meetupId, userId);
		Instant now = clock.instant();
		if (meetup.isStarted(now) || !participation.findConfirmedUsers(meetupId).isEmpty()) {
			throw new BusinessException(MeetupErrorCode.MEETUP_DELETE_NOT_ALLOWED);
		}
		meetup.delete(now);
		notifications.notifyAll(participation.findPendingUsers(meetupId), NotificationType.MEETUP_DELETED, userId, meetupId);
	}

	private Meetup lockForHost(Long meetupId, Long userId) {
		Meetup meetup = meetups.findVisibleForUpdate(meetupId)
				.orElseThrow(() -> new BusinessException(MeetupErrorCode.MEETUP_NOT_FOUND));
		if (!meetup.getHostId().equals(userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN);
		}
		return meetup;
	}

	private List<Long> recipients(Long meetupId, List<Long> confirmed) {
		List<Long> receivers = new ArrayList<>(confirmed);
		receivers.addAll(participation.findPendingUsers(meetupId));
		return receivers;
	}

	private static void validateDetails(MeetupDetails details, Instant now, List<FieldError> errors) {
		if (!details.startsAt().isAfter(now)) {
			errors.add(new FieldError("startsAt", "지금보다 뒤여야 합니다."));
		}
		if (details.endsAt() != null && !details.endsAt().isAfter(details.startsAt())) {
			errors.add(new FieldError("endsAt", "시작 시각보다 뒤여야 합니다."));
		}
		if (details.mode() == MeetupMode.OFFLINE) {
			if (details.place() == null || details.place().isBlank()) {
				errors.add(new FieldError("place", "오프라인 장소를 입력해야 합니다."));
			}
			if (details.joinUrl() != null) {
				errors.add(new FieldError("joinUrl", "오프라인 모임은 접속 링크를 받지 않습니다."));
			}
		}
		else {
			if (details.place() != null) {
				errors.add(new FieldError("place", "온라인 모임은 장소를 받지 않습니다."));
			}
			if (details.placeDetail() != null) {
				errors.add(new FieldError("placeDetail", "온라인 모임은 상세 주소를 받지 않습니다."));
			}
			if (details.joinUrl() != null && !details.joinUrl().startsWith("https://")
					&& !details.joinUrl().startsWith("http://")) {
				errors.add(new FieldError("joinUrl", "http:// 또는 https://로 시작해야 합니다."));
			}
		}
	}

	private static void rejectErrors(List<FieldError> errors) {
		if (!errors.isEmpty()) {
			errors.sort(Comparator.comparing(FieldError::field));
			throw BusinessException.invalidInput(errors);
		}
	}

}
