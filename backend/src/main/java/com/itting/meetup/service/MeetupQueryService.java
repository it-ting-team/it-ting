package com.itting.meetup.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.itting.global.error.BusinessException;
import com.itting.interest.dto.InterestTagResponse;
import com.itting.interest.service.InterestTagService;
import com.itting.meetup.domain.Meetup;
import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupDetails;
import com.itting.meetup.domain.MeetupErrorCode;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.domain.MeetupSort;
import com.itting.meetup.domain.MeetupViewerStatus;
import com.itting.meetup.domain.RecruitStatus;
import com.itting.meetup.dto.MeetupDetailResponse;
import com.itting.meetup.dto.MeetupDetailResponse.ParticipantResponse;
import com.itting.meetup.dto.MeetupListResponse;
import com.itting.meetup.repository.MeetupListRepository;
import com.itting.meetup.repository.MeetupParticipationRepository;
import com.itting.meetup.repository.MeetupRepository;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.UserDisplayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MeetupQueryService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final MeetupRepository meetups;
	private final MeetupListRepository lists;
	private final MeetupParticipationRepository participation;
	private final UserDisplayService displays;
	private final InterestTagService interests;
	private final MeetupChatRooms chatRooms;
	private final Clock clock;

	public MeetupQueryService(MeetupRepository meetups, MeetupListRepository lists,
			MeetupParticipationRepository participation, UserDisplayService displays, InterestTagService interests,
			MeetupChatRooms chatRooms, Clock clock) {
		this.meetups = meetups;
		this.lists = lists;
		this.participation = participation;
		this.displays = displays;
		this.interests = interests;
		this.chatRooms = chatRooms;
		this.clock = clock;
	}

	public MeetupListResponse list(String keyword, List<MeetupCategory> categories, LocalDate from, LocalDate through,
			MeetupMode mode, MeetupSort sort, int page, int size) {
		if (from != null && through != null && through.isBefore(from)) {
			throw BusinessException.invalidInput("dateTo", "시작 날짜보다 앞설 수 없습니다.");
		}
		if (size != 6 && size != 12 && size != 24) {
			throw BusinessException.invalidInput("size", "6, 12, 24 가운데 하나여야 합니다.");
		}
		Instant startsFrom = from == null ? null : from.atStartOfDay(SEOUL).toInstant();
		Instant startsUntil = through == null ? null : through.plusDays(1).atStartOfDay(SEOUL).toInstant();
		MeetupListRepository.Search search = new MeetupListRepository.Search(keyword == null ? null : keyword.strip(),
				categories == null ? List.of() : categories, startsFrom, startsUntil, mode);
		MeetupListRepository.Page result = lists.search(search, clock.instant(), sort, (page - 1L) * size, size);
		return new MeetupListResponse(result.items(), page, size, result.totalItems(),
				(result.totalItems() + size - 1) / size);
	}

	public MeetupDetailResponse detail(Long meetupId, Long viewerId) {
		Meetup meetup = meetups.findByIdAndDeletedAtIsNull(meetupId)
				.orElseThrow(() -> new BusinessException(MeetupErrorCode.MEETUP_NOT_FOUND));
		Instant now = clock.instant();
		boolean host = meetup.getHostId().equals(viewerId);
		MeetupViewerStatus status = host ? MeetupViewerStatus.HOST : MeetupViewerStatus.fromApplication(
				participation.findStatus(meetupId, viewerId).orElse(null), meetup.isRecruitmentEnded(now));
		boolean confirmed = status == MeetupViewerStatus.CONFIRMED;
		List<ParticipantResponse> participants = participants(meetup, viewerId);
		long confirmedCount = participants.size();
		RecruitStatus recruitStatus = meetup.recruitStatus(now, confirmedCount);
		Optional<Boolean> vacancy = participation.findVacancySubscription(meetupId, viewerId);
		Long chatRoomId = chatRooms.findJoinedRoom(meetupId, viewerId).orElse(null);
		MeetupDetails d = meetup.getDetails();
		return new MeetupDetailResponse(meetupId, d.title(), d.category(), d.description(), d.supplies(),
				d.isMessageEnabled(), d.startsAt(), d.endsAt(), meetup.getRecruitDeadlineAt(), d.mode(), d.place(),
				d.placeDetail(), host || confirmed ? d.joinUrl() : null, d.capacity(), confirmedCount, recruitStatus,
				participants.getFirst().user(), participants,
				participants.stream().filter(ParticipantResponse::hasSharedInterest).count(),
				vacancy.isPresent(), vacancy.orElse(false), status,
				status == MeetupViewerStatus.NONE && recruitStatus == RecruitStatus.OPEN,
				confirmed && meetup.canCancelParticipation(now), host && meetup.canEdit(now),
				host && !meetup.isCanceled() && !meetup.isStarted(now) && confirmedCount > 1,
				host && !meetup.isStarted(now) && confirmedCount == 1, chatRoomId);
	}

	private List<ParticipantResponse> participants(Meetup meetup, Long viewerId) {
		List<Long> ids = new ArrayList<>();
		ids.add(meetup.getHostId());
		ids.addAll(participation.findConfirmedUsers(meetup.getId()));
		Map<Long, UserDisplayResponse> users = displays.getAll(ids);
		Map<Long, List<InterestTagResponse>> shared = interests.getTagsForOwners(ids, viewerId);
		return ids.stream().map(id -> new ParticipantResponse(users.get(id), id.equals(meetup.getHostId()),
				id.equals(viewerId), shared.getOrDefault(id, List.of()).stream().anyMatch(InterestTagResponse::isShared)))
				.toList();
	}

}
