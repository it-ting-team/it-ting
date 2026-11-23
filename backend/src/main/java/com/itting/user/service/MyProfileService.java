package com.itting.user.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.itting.burrow.domain.Burrow;
import com.itting.burrow.dto.MyBurrowResponse;
import com.itting.burrow.repository.BurrowRepository;
import com.itting.burrow.service.BurrowService;
import com.itting.burrow.service.MyBurrowService;
import com.itting.campus.domain.CampusErrorCode;
import com.itting.campus.repository.CampusFloorRepository;
import com.itting.global.error.BusinessException;
import com.itting.global.response.ApiError;
import com.itting.interest.domain.InterestErrorCode;
import com.itting.interest.domain.UserInterest;
import com.itting.interest.repository.InterestRepository;
import com.itting.interest.repository.UserInterestRepository;
import com.itting.question.domain.IntroQuestionErrorCode;
import com.itting.question.domain.UserAnswer;
import com.itting.question.repository.IntroQuestionRepository;
import com.itting.question.repository.UserAnswerRepository;
import com.itting.term.service.TermService;
import com.itting.user.domain.UserErrorCode;
import com.itting.user.domain.UserProfile;
import com.itting.user.dto.MoleResponse;
import com.itting.user.dto.MoleUpdateRequest;
import com.itting.user.dto.ProfileUpdateRequest;
import com.itting.user.repository.UserProfileRepository;
import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

/**
 * 내 두더지와 내 정보를 저장한다({@code PATCH /api/v1/me/mole}, {@code PATCH /api/v1/me/profile}).
 *
 * <p>내 정보 저장은 프로필 · 관심사 · 답변 · 땅굴 입구를 한 트랜잭션으로 쓴다. 명세(04 내 정보 수정 2)가 캠퍼스-층을 바꾸면
 * 땅굴이 새 들판으로 옮겨진다고 정했고, API 명세의 "캠퍼스-층을 저장할 때"가 이를 한 API로 정했다.
 */
@Service
public class MyProfileService {

	private final UserProfileRepository userProfileRepository;

	private final CampusFloorRepository campusFloorRepository;

	private final InterestRepository interestRepository;

	private final UserInterestRepository userInterestRepository;

	private final IntroQuestionRepository introQuestionRepository;

	private final UserAnswerRepository userAnswerRepository;

	private final BurrowRepository burrowRepository;

	private final BurrowService burrowService;

	private final MyBurrowService myBurrowService;

	private final TermService termService;

	private final NicknameService nicknameService;

	private final SpringValidatorAdapter validator;

	private final TransactionTemplate transaction;

	public MyProfileService(UserProfileRepository userProfileRepository, CampusFloorRepository campusFloorRepository,
			InterestRepository interestRepository, UserInterestRepository userInterestRepository,
			IntroQuestionRepository introQuestionRepository, UserAnswerRepository userAnswerRepository,
			BurrowRepository burrowRepository, BurrowService burrowService, MyBurrowService myBurrowService,
			TermService termService, NicknameService nicknameService, Validator validator,
			PlatformTransactionManager transactionManager) {
		this.userProfileRepository = userProfileRepository;
		this.campusFloorRepository = campusFloorRepository;
		this.interestRepository = interestRepository;
		this.userInterestRepository = userInterestRepository;
		this.introQuestionRepository = introQuestionRepository;
		this.userAnswerRepository = userAnswerRepository;
		this.burrowRepository = burrowRepository;
		this.burrowService = burrowService;
		this.myBurrowService = myBurrowService;
		this.termService = termService;
		this.nicknameService = nicknameService;
		this.validator = new SpringValidatorAdapter(validator);
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** 두더지 네 부위를 보낸 값으로 바꾼다. 빈 값을 보낸 부위는 비운다. 입력 검사는 컨트롤러가 한다. */
	@Transactional
	public MoleResponse changeMole(Long userId, MoleUpdateRequest request) {
		UserProfile profile = findProfile(userId);
		profile.changeMole(request.head().orElse(null), request.face().orElse(null), request.neck().orElse(null),
				request.hand().orElse(null));
		return MoleResponse.from(profile);
	}

	/**
	 * 내 정보를 저장하고 바뀐 내 땅굴 전체를 준다.
	 *
	 * @throws BusinessException 입력이 어긋나면 {@code INVALID_INPUT}, 없는 캠퍼스-층 · 관심사 · 질문이면 각각의
	 *         {@code ..._NOT_FOUND}, 닉네임을 다른 사람이 쓰거나 맡아 두었으면 {@code NICKNAME_DUPLICATED}
	 */
	public MyBurrowResponse changeProfile(Long userId, ProfileUpdateRequest request) {
		validate(request);
		boolean nicknameChanged = Boolean.TRUE.equals(transaction.execute(status -> save(userId, request)));
		if (nicknameChanged) {
			nicknameService.release(userId);
		}
		return myBurrowService.getMyBurrow(userId);
	}

	/**
	 * 프로필 행을 잠그고 저장한다. 같은 사람의 저장이 동시에 와도 차례로 처리한다.
	 *
	 * @return 닉네임을 바꿨으면 true. 커밋한 뒤 맡아 둔 닉네임을 푼다
	 */
	private boolean save(Long userId, ProfileUpdateRequest request) {
		UserProfile profile = userProfileRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new IllegalStateException("프로필이 없는 계정: " + userId));

		assertCampusFloorExists(request.campusFloorId());
		assertInterestsExist(request.interestIds());
		assertQuestionsExist(request.answers());

		String nickname = request.nickname();
		boolean nicknameChanged = !nickname.equals(profile.getNickname());
		if (nicknameChanged) {
			// 맡은 뒤에 DB를 본다. 온보딩 확정과 같다(NicknameService.holdForSave).
			nicknameService.holdForSave(userId, nickname);
			if (userProfileRepository.existsByNicknameAndUserIdNot(nickname, userId)) {
				throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
			}
		}

		boolean campusFloorChanged = !request.campusFloorId().equals(profile.getCampusFloorId());
		profile.changeProfile(nickname, request.campusFloorId(), request.intro().orElse(null));
		if (nicknameChanged) {
			saveNickname();
		}

		saveInterests(userId, request.interestIds());
		// 영속성 컨텍스트를 비우므로 프로필 · 관심사를 모두 고친 뒤에 부른다.
		saveAnswers(userId, request.answers());

		if (campusFloorChanged) {
			Burrow burrow = burrowRepository.findByUserId(userId)
					.orElseThrow(() -> new IllegalStateException("온보딩을 마쳤는데 땅굴이 없는 계정: " + userId));
			Long termId = termService.findCurrentTermId()
					.orElseThrow(() -> new IllegalStateException("열린 학기가 없어 땅굴 입구를 옮길 수 없습니다."));
			burrowService.moveEntrance(burrow.getId(), termId, request.campusFloorId());
		}
		return nicknameChanged;
	}

	/** 닉네임을 바로 DB에 써서, 확인한 뒤 다른 사람이 같은 닉네임을 먼저 저장했으면 UNIQUE로 알아챈다. */
	private void saveNickname() {
		try {
			userProfileRepository.flush();
		}
		catch (DataIntegrityViolationException e) {
			if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uk_user_profile_nickname")) {
				throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
			}
			throw e;
		}
	}

	/** 빠진 관심사의 줄은 지우고, 새 관심사는 줄을 만들고, 모든 줄의 순서를 보낸 순서(1부터)로 맞춘다. */
	private void saveInterests(Long userId, List<Long> interestIds) {
		Map<Long, UserInterest> current = new HashMap<>();
		userInterestRepository.findAllByUserIdOrderBySortOrderAsc(userId)
				.forEach(userInterest -> current.put(userInterest.getInterestId(), userInterest));

		List<UserInterest> added = new ArrayList<>();
		for (int i = 0; i < interestIds.size(); i++) {
			UserInterest kept = current.remove(interestIds.get(i));
			if (kept != null) {
				kept.changeSortOrder(i + 1);
			}
			else {
				added.add(UserInterest.create(userId, interestIds.get(i), i + 1));
			}
		}
		userInterestRepository.deleteAll(current.values());
		userInterestRepository.saveAll(added);
	}

	/** 지금 답변을 모두 지우고 보낸 순서대로 자리 1 ~ 3에 다시 넣는다. */
	private void saveAnswers(Long userId, List<ProfileUpdateRequest.Answer> answers) {
		userAnswerRepository.deleteAllByUserIdNow(userId);
		List<UserAnswer> userAnswers = new ArrayList<>(answers.size());
		for (int i = 0; i < answers.size(); i++) {
			ProfileUpdateRequest.Answer answer = answers.get(i);
			userAnswers.add(UserAnswer.create(userId, i + 1, answer.introQuestionId(), answer.answer()));
		}
		userAnswerRepository.saveAll(userAnswers);
	}

	private UserProfile findProfile(Long userId) {
		return userProfileRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("프로필이 없는 계정: " + userId));
	}

	/** Bean Validation과 세 질문이 서로 다른지를 보고, 어긋난 것을 {@code fields}에 모두 담는다. */
	private void validate(ProfileUpdateRequest request) {
		Errors errors = new BeanPropertyBindingResult(request, "request");
		validator.validate(request, errors);
		List<ApiError.FieldError> fields = new ArrayList<>();
		errors.getFieldErrors().forEach(error -> fields.add(new ApiError.FieldError(error.getField(),
				error.getDefaultMessage())));
		if (request.answers() != null && request.answers().stream().allMatch(answer -> answer != null
				&& answer.introQuestionId() != null)) {
			long distinct = request.answers().stream().map(ProfileUpdateRequest.Answer::introQuestionId)
					.distinct().count();
			if (distinct != request.answers().size()) {
				fields.add(new ApiError.FieldError("answers", "서로 다른 질문을 골라야 합니다."));
			}
		}
		if (!fields.isEmpty()) {
			fields.sort(Comparator.comparing(ApiError.FieldError::field));
			throw BusinessException.invalidInput(fields);
		}
	}

	private void assertCampusFloorExists(Long campusFloorId) {
		if (campusFloorRepository.findById(campusFloorId).filter(floor -> floor.getDeletedAt() == null).isEmpty()) {
			throw new BusinessException(CampusErrorCode.CAMPUS_FLOOR_NOT_FOUND);
		}
	}

	/** 모두 지우지 않은 세부 관심사인지. 같은 id가 두 번 오지 않는 것은 입력 검사에서 봤다. */
	private void assertInterestsExist(List<Long> interestIds) {
		long found = interestRepository.findAllById(interestIds).stream()
				.filter(interest -> interest.getDeletedAt() == null)
				.count();
		if (found != interestIds.size()) {
			throw new BusinessException(InterestErrorCode.INTEREST_NOT_FOUND);
		}
	}

	private void assertQuestionsExist(List<ProfileUpdateRequest.Answer> answers) {
		HashSet<Long> ids = new HashSet<>();
		answers.forEach(answer -> ids.add(answer.introQuestionId()));
		if (introQuestionRepository.findAllById(ids).size() != ids.size()) {
			throw new BusinessException(IntroQuestionErrorCode.INTRO_QUESTION_NOT_FOUND);
		}
	}

}
