package com.itting.burrow.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.itting.burrow.domain.DailyRecommendationMember;
import com.itting.burrow.dto.DailyRecommendationRequest;
import com.itting.burrow.dto.DailyRecommendationResponse;
import com.itting.burrow.dto.DailyRecommendationResponse.RecommendationCardResponse;
import com.itting.burrow.dto.MyBurrowResponse.AnswerResponse;
import com.itting.burrow.repository.DailyRecommendationMemberRepository;
import com.itting.burrow.repository.DailyRecommendationMemberRepository.Member;
import com.itting.burrow.repository.DailyRecommendationRepository;
import com.itting.campus.service.CampusService;
import com.itting.generation.service.GenerationService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.interest.dto.InterestTagResponse;
import com.itting.interest.service.InterestTagService;
import com.itting.question.repository.UserAnswerRepository;
import com.itting.question.repository.UserAnswerRow;
import com.itting.term.service.TermService;
import com.itting.user.domain.User;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.repository.UserRepository;
import com.itting.user.service.UserDisplayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DailyRecommendationService {

	private static final ZoneId RECOMMENDATION_ZONE = ZoneId.of("Asia/Seoul");

	private final DailyRecommendationRepository recommendations;
	private final DailyRecommendationMemberRepository members;
	private final UserRepository users;
	private final TermService terms;
	private final CampusService campuses;
	private final GenerationService generations;
	private final UserDisplayService userDisplays;
	private final InterestTagService interestTags;
	private final UserAnswerRepository answers;
	private final Clock clock;

	public DailyRecommendationService(DailyRecommendationRepository recommendations,
			DailyRecommendationMemberRepository members, UserRepository users, TermService terms,
			CampusService campuses, GenerationService generations, UserDisplayService userDisplays,
			InterestTagService interestTags, UserAnswerRepository answers, Clock clock) {
		this.recommendations = recommendations;
		this.members = members;
		this.users = users;
		this.terms = terms;
		this.campuses = campuses;
		this.generations = generations;
		this.userDisplays = userDisplays;
		this.interestTags = interestTags;
		this.answers = answers;
		this.clock = clock;
	}

	public DailyRecommendationResponse get(Long userId) {
		LocalDate date = today();
		return recommendations.findByUserIdAndRecommendedOn(userId, date)
				.map(existing -> received(userId, date, existing.getId()))
				.orElseGet(() -> new DailyRecommendationResponse(false, date, List.of()));
	}

	@Transactional
	public Result receive(Long userId, Supplier<DailyRecommendationRequest> readRequest) {
		// 공유 잠금이면 서로를 추천받는 두 요청의 대상 계정 외래 키 검사도 서로 막지 않는다.
		users.findByIdForShare(userId).filter(User::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
		Instant now = clock.instant();
		LocalDate date = now.atZone(RECOMMENDATION_ZONE).toLocalDate();
		// 계정 전체 대신 사용자·날짜의 고유 키로 최초 요청을 가른다. 입력이 틀리면 삽입도 롤백한다.
		boolean created = recommendations.insertIfAbsent(userId, date, now) == 1;
		Long recommendationId = recommendations.findByUserIdAndRecommendedOn(userId, date)
				.orElseThrow(() -> new IllegalStateException("추천 날짜 기록을 읽을 수 없습니다.")).getId();
		if (!created) {
			return new Result(false, received(userId, date, recommendationId));
		}

		DailyRecommendationRequest request = readRequest.get();
		if (!generations.isAvailable(request.generation())) {
			throw BusinessException.invalidInput("generation", "고를 수 있는 기수가 아닙니다.");
		}
		campuses.getAvailableFloor(request.campusFloorId());
		Long termId = terms.findCurrentTermId()
				.orElseThrow(() -> new IllegalStateException("열린 학기가 없어 추천을 받을 수 없습니다."));
		List<Long> candidates = recommendations.selectCandidates(userId, termId, request.campusFloorId(),
				request.generation());
		List<DailyRecommendationMember> selected = new ArrayList<>(candidates.size());
		for (int i = 0; i < candidates.size(); i++) {
			selected.add(new DailyRecommendationMember(recommendationId, i + 1, candidates.get(i)));
		}
		members.saveAll(selected);
		return new Result(true, received(userId, date, recommendationId));
	}

	private DailyRecommendationResponse received(Long userId, LocalDate date, Long recommendationId) {
		List<Member> visible = members.findVisibleMembers(recommendationId);
		if (visible.isEmpty()) {
			return new DailyRecommendationResponse(true, date, List.of());
		}
		List<Long> ids = visible.stream().map(Member::getUserId).toList();
		Map<Long, UserDisplayResponse> owners = userDisplays.getAll(ids);
		Map<Long, List<InterestTagResponse>> tags = interestTags.getTagsForOwners(ids, userId);
		Map<Long, List<AnswerResponse>> answersByUser = answers.findRowsByUserIds(ids).stream()
				.collect(Collectors.groupingBy(UserAnswerRow::userId, Collectors.mapping(
						row -> new AnswerResponse(row.slot(), row.introQuestionId(), row.question(), row.answer()),
						Collectors.toList())));
		List<RecommendationCardResponse> items = visible.stream()
				.map(member -> new RecommendationCardResponse(member.getBurrowId(), owners.get(member.getUserId()),
						tags.getOrDefault(member.getUserId(), List.of()).stream()
								.sorted(Comparator.comparing(InterestTagResponse::isShared).reversed())
								.limit(3).toList(),
						answersByUser.getOrDefault(member.getUserId(), List.of())))
				.toList();
		return new DailyRecommendationResponse(true, date, items);
	}

	private LocalDate today() {
		return clock.instant().atZone(RECOMMENDATION_ZONE).toLocalDate();
	}

	public record Result(boolean created, DailyRecommendationResponse response) {
	}

}
