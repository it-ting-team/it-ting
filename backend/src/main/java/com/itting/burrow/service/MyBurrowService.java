package com.itting.burrow.service;

import java.util.List;

import com.itting.burrow.domain.Burrow;
import com.itting.burrow.domain.BurrowEntrance;
import com.itting.burrow.dto.MyBurrowResponse;
import com.itting.burrow.repository.BurrowEntranceRepository;
import com.itting.burrow.repository.BurrowRepository;
import com.itting.burrow.repository.RegularBurrowRepository;
import com.itting.guestbook.domain.GuestbookEntryStatus;
import com.itting.guestbook.repository.GuestbookEntryRepository;
import com.itting.interest.service.InterestTagService;
import com.itting.question.repository.UserAnswerRepository;
import com.itting.user.domain.UserProfile;
import com.itting.user.repository.UserProfileRepository;
import com.itting.user.service.UserDisplayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 땅굴과 내 정보 카드의 값을 모은다({@code GET /api/v1/me/burrow}). 내 정보를 저장한 뒤의 응답도 이것으로 만든다.
 * 읽기만 한다.
 */
@Service
@Transactional(readOnly = true)
public class MyBurrowService {

	private final BurrowRepository burrowRepository;

	private final BurrowEntranceRepository burrowEntranceRepository;

	private final UserProfileRepository userProfileRepository;

	private final UserAnswerRepository userAnswerRepository;

	private final GuestbookEntryRepository guestbookEntryRepository;
	private final RegularBurrowRepository regularBurrowRepository;

	private final UserDisplayService userDisplayService;

	private final InterestTagService interestTagService;

	public MyBurrowService(BurrowRepository burrowRepository, BurrowEntranceRepository burrowEntranceRepository,
			UserProfileRepository userProfileRepository, UserAnswerRepository userAnswerRepository,
			GuestbookEntryRepository guestbookEntryRepository, UserDisplayService userDisplayService,
			InterestTagService interestTagService, RegularBurrowRepository regularBurrowRepository) {
		this.burrowRepository = burrowRepository;
		this.burrowEntranceRepository = burrowEntranceRepository;
		this.userProfileRepository = userProfileRepository;
		this.userAnswerRepository = userAnswerRepository;
		this.guestbookEntryRepository = guestbookEntryRepository;
		this.userDisplayService = userDisplayService;
		this.interestTagService = interestTagService;
		this.regularBurrowRepository = regularBurrowRepository;
	}

	/**
	 * 세션의 계정으로 땅굴을 찾아 그 땅굴 하나의 값만 준다. 이 API는 온보딩을 마치고 이번 학기를 고른 계정만 부르므로
	 * 땅굴과 입구가 늘 있다.
	 *
	 * @throws IllegalStateException 땅굴이나 입구가 없을 때. 생기면 안 되는 상태라 {@code 500}이다
	 */
	public MyBurrowResponse getMyBurrow(Long userId) {
		Burrow burrow = burrowRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalStateException("온보딩을 마쳤는데 땅굴이 없는 계정: " + userId));
		BurrowEntrance entrance = burrowEntranceRepository.findByBurrowId(burrow.getId())
				.orElseThrow(() -> new IllegalStateException("입구가 없는 땅굴: " + burrow.getId()));
		UserProfile profile = userProfileRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("프로필이 없는 계정: " + userId));

		List<MyBurrowResponse.AnswerResponse> answers = userAnswerRepository.findRowsByUserIds(List.of(userId)).stream()
				.map(row -> new MyBurrowResponse.AnswerResponse(row.slot(), row.introQuestionId(), row.question(),
						row.answer()))
				.toList();
		long guestbookCount = guestbookEntryRepository.countByBurrowIdAndStatus(burrow.getId(),
				GuestbookEntryStatus.PUBLIC);

		return new MyBurrowResponse(burrow.getId(), userDisplayService.get(userId),
				profile.getCampusFloorId(), profile.getIntro(), interestTagService.getMyTags(userId), answers,
				regularBurrowRepository.countByBurrowId(burrow.getId()), guestbookCount,
				new MyBurrowResponse.EntranceResponse(entrance.getTermId(), entrance.getCampusFloorId(),
						entrance.getSlot()));
	}

}
