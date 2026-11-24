package com.itting.burrow.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.itting.burrow.domain.Burrow;
import com.itting.burrow.domain.BurrowEntrance;
import com.itting.burrow.domain.BurrowErrorCode;
import com.itting.burrow.dto.BurrowVisitResponse;
import com.itting.burrow.dto.BurrowVisitResponse.ChatResponse;
import com.itting.burrow.dto.BurrowVisitResponse.ChatStatus;
import com.itting.burrow.dto.FieldDetailResponse;
import com.itting.burrow.dto.FieldDetailResponse.FieldEntranceResponse;
import com.itting.burrow.dto.FieldListResponse;
import com.itting.burrow.dto.FieldListResponse.FieldResponse;
import com.itting.burrow.dto.MyBurrowResponse.AnswerResponse;
import com.itting.burrow.dto.MyBurrowResponse.EntranceResponse;
import com.itting.burrow.repository.BurrowEntranceRepository;
import com.itting.burrow.repository.BurrowRepository;
import com.itting.burrow.repository.FieldEntranceRow;
import com.itting.campus.repository.CampusFloorLocation;
import com.itting.campus.service.CampusService;
import com.itting.global.error.BusinessException;
import com.itting.guestbook.domain.GuestbookEntryStatus;
import com.itting.guestbook.repository.GuestbookEntryRepository;
import com.itting.interest.dto.InterestTagResponse;
import com.itting.interest.service.InterestTagService;
import com.itting.question.repository.UserAnswerRepository;
import com.itting.question.repository.UserAnswerRow;
import com.itting.term.dto.TermListResponse.TermResponse;
import com.itting.term.service.TermService;
import com.itting.user.domain.UserProfile;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.repository.UserProfileRepository;
import com.itting.user.repository.UserRepository;
import com.itting.user.service.UserDisplayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BurrowExploreService {

	private final TermService termService;
	private final CampusService campusService;
	private final BurrowEntranceRepository entranceRepository;
	private final BurrowRepository burrowRepository;
	private final UserRepository userRepository;
	private final UserProfileRepository profileRepository;
	private final UserDisplayService userDisplayService;
	private final InterestTagService interestTagService;
	private final UserAnswerRepository answerRepository;
	private final GuestbookEntryRepository guestbookRepository;

	public BurrowExploreService(TermService termService, CampusService campusService,
			BurrowEntranceRepository entranceRepository, BurrowRepository burrowRepository, UserRepository userRepository,
			UserProfileRepository profileRepository, UserDisplayService userDisplayService,
			InterestTagService interestTagService, UserAnswerRepository answerRepository,
			GuestbookEntryRepository guestbookRepository) {
		this.termService = termService;
		this.campusService = campusService;
		this.entranceRepository = entranceRepository;
		this.burrowRepository = burrowRepository;
		this.userRepository = userRepository;
		this.profileRepository = profileRepository;
		this.userDisplayService = userDisplayService;
		this.interestTagService = interestTagService;
		this.answerRepository = answerRepository;
		this.guestbookRepository = guestbookRepository;
	}

	public FieldListResponse getFields(Long termId) {
		termService.getOpenedTerm(termId);
		Set<Long> occupiedFloors = Set.copyOf(entranceRepository.findOccupiedFloorIds(termId));
		List<FieldResponse> fields = campusService.getCampuses().items().stream()
				.flatMap(campus -> campus.floors().stream())
				.map(floor -> new FieldResponse(floor.id(), !occupiedFloors.contains(floor.id())))
				.toList();
		return new FieldListResponse(fields);
	}

	public FieldDetailResponse getField(Long viewerId, Long termId, Long campusFloorId) {
		TermResponse term = termService.getOpenedTerm(termId);
		CampusFloorLocation location = campusService.getAvailableFloor(campusFloorId);
		List<FieldEntranceRow> entrances = entranceRepository.findFieldEntrances(termId, campusFloorId);
		if (entrances.isEmpty()) {
			return new FieldDetailResponse(term, campusFloorId, location.campusName(), location.floor(), List.of());
		}
		List<Long> ownerIds = entrances.stream().map(FieldEntranceRow::ownerId).toList();
		Map<Long, UserDisplayResponse> owners = userDisplayService.getAll(ownerIds);
		Map<Long, List<InterestTagResponse>> interests = interestTagService.getTagsForOwners(ownerIds, viewerId);
		Map<Long, List<AnswerResponse>> answers = answerRepository.findRowsByUserIds(ownerIds).stream()
				.collect(Collectors.groupingBy(UserAnswerRow::userId,
						Collectors.mapping(BurrowExploreService::toAnswerResponse, Collectors.toList())));
		List<FieldEntranceResponse> items = entrances.stream()
				.map(entrance -> new FieldEntranceResponse(entrance.slot(), entrance.burrowId(),
						viewerId.equals(entrance.ownerId()), owners.get(entrance.ownerId()), entrance.intro(),
						interests.getOrDefault(entrance.ownerId(), List.of()),
						answers.getOrDefault(entrance.ownerId(), List.of())))
				.toList();
		return new FieldDetailResponse(term, campusFloorId, location.campusName(), location.floor(), items);
	}

	public BurrowVisitResponse visit(Long viewerId, Long ownerId) {
		userRepository.findById(ownerId).filter(user -> user.isActive() && user.isOnboarded())
				.orElseThrow(() -> new BusinessException(BurrowErrorCode.BURROW_NOT_FOUND));
		Burrow burrow = burrowRepository.findByUserId(ownerId)
				.orElseThrow(() -> new BusinessException(BurrowErrorCode.BURROW_NOT_FOUND));
		BurrowEntrance entrance = entranceRepository.findByBurrowId(burrow.getId())
				.orElseThrow(() -> new IllegalStateException("입구가 없는 땅굴: " + burrow.getId()));
		UserProfile profile = profileRepository.findById(ownerId)
				.orElseThrow(() -> new IllegalStateException("프로필이 없는 계정: " + ownerId));
		List<AnswerResponse> answers = answerRepository.findRowsByUserIds(List.of(ownerId)).stream()
				.map(BurrowExploreService::toAnswerResponse)
				.toList();
		long guestbookCount = guestbookRepository.countByBurrowIdAndStatus(burrow.getId(), GuestbookEntryStatus.PUBLIC);

		// 단골과 채팅이 추가되면 각각의 저장된 관계를 읽는다. KAN-47, KAN-55, KAN-56.
		return new BurrowVisitResponse(burrow.getId(), viewerId.equals(ownerId), userDisplayService.get(ownerId),
				profile.getIntro(), interestTagService.getTags(ownerId, viewerId), answers, guestbookCount,
				false, new ChatResponse(ChatStatus.NONE, null),
				new EntranceResponse(entrance.getTermId(), entrance.getCampusFloorId(), entrance.getSlot()));
	}

	private static AnswerResponse toAnswerResponse(UserAnswerRow row) {
		return new AnswerResponse(row.slot(), row.introQuestionId(), row.question(), row.answer());
	}

}
