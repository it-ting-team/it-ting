package com.itting.onboarding.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import com.itting.auth.domain.NextStep;
import com.itting.burrow.domain.Burrow;
import com.itting.burrow.repository.BurrowRepository;
import com.itting.burrow.service.BurrowService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.global.response.ApiError;
import com.itting.interest.domain.InterestErrorCode;
import com.itting.interest.domain.UserInterest;
import com.itting.interest.repository.InterestRepository;
import com.itting.interest.repository.UserInterestRepository;
import com.itting.onboarding.dto.OnboardingCompleteRequest;
import com.itting.onboarding.dto.OnboardingCompleteResponse;
import com.itting.question.domain.IntroQuestionErrorCode;
import com.itting.question.domain.UserAnswer;
import com.itting.question.repository.IntroQuestionRepository;
import com.itting.question.repository.UserAnswerRepository;
import com.itting.term.service.TermService;
import com.itting.user.domain.User;
import com.itting.user.domain.UserErrorCode;
import com.itting.user.domain.UserProfile;
import com.itting.user.repository.UserProfileRepository;
import com.itting.user.repository.UserRepository;
import com.itting.user.service.NicknameService;
import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

/**
 * 온보딩에서 정한 값을 한 번에 저장하고 땅굴을 만든다({@code POST /api/v1/me/onboarding/complete}).
 *
 * <p>프로필 · 관심사 · 답변 · 땅굴 · 입구 · 계정을 한 트랜잭션으로 쓴다. 여러 영역을 한 API에서 쓰는 까닭은
 * {@code docs/api/03-onboarding.md}의 "확정 API 하나로 저장"(DB 설계 A4)에 있다.
 */
@Service
public class OnboardingService {

	private final UserRepository userRepository;

	private final UserProfileRepository userProfileRepository;

	private final InterestRepository interestRepository;

	private final UserInterestRepository userInterestRepository;

	private final IntroQuestionRepository introQuestionRepository;

	private final UserAnswerRepository userAnswerRepository;

	private final BurrowRepository burrowRepository;

	private final BurrowService burrowService;

	private final TermService termService;

	private final NicknameService nicknameService;

	private final SpringValidatorAdapter validator;

	private final TransactionTemplate transaction;

	private final TransactionTemplate readOnlyTransaction;

	private final Clock clock;

	public OnboardingService(UserRepository userRepository, UserProfileRepository userProfileRepository,
			InterestRepository interestRepository, UserInterestRepository userInterestRepository,
			IntroQuestionRepository introQuestionRepository, UserAnswerRepository userAnswerRepository,
			BurrowRepository burrowRepository, BurrowService burrowService, TermService termService,
			NicknameService nicknameService, Validator validator, PlatformTransactionManager transactionManager,
			Clock clock) {
		this.userRepository = userRepository;
		this.userProfileRepository = userProfileRepository;
		this.interestRepository = interestRepository;
		this.userInterestRepository = userInterestRepository;
		this.introQuestionRepository = introQuestionRepository;
		this.userAnswerRepository = userAnswerRepository;
		this.burrowRepository = burrowRepository;
		this.burrowService = burrowService;
		this.termService = termService;
		this.nicknameService = nicknameService;
		this.validator = new SpringValidatorAdapter(validator);
		this.transaction = new TransactionTemplate(transactionManager);
		this.readOnlyTransaction = new TransactionTemplate(transactionManager);
		this.readOnlyTransaction.setReadOnly(true);
		this.clock = clock;
	}

	/**
	 * 이미 온보딩을 마친 계정이면 본문을 보지 않고 지금의 땅굴로 답한다. 버튼을 두 번 눌러도 땅굴이 둘 생기지 않는다.
	 *
	 * @throws BusinessException 입력이 어긋나면 {@code INVALID_INPUT}, 없는 관심사면 {@code INTEREST_NOT_FOUND},
	 *         없는 질문이면 {@code INTRO_QUESTION_NOT_FOUND}, 닉네임을 다른 사람이 쓰거나 맡아 두었으면
	 *         {@code NICKNAME_DUPLICATED}
	 * @throws IllegalStateException 열린 학기가 없어 입구를 놓을 수 없을 때. 아무것도 저장하지 않고 {@code 500}이다
	 */
	public Result complete(Long userId, OnboardingCompleteRequest request) {
		Optional<Result> done = readOnlyTransaction.execute(status -> findCompleted(findActiveUser(userId)));
		if (done.isPresent()) {
			return done.get();
		}
		validate(request);
		Result result = transaction.execute(status -> save(userId, request));
		if (result.created()) {
			nicknameService.release(userId);
		}
		return result;
	}

	/** 계정 행을 잠근 뒤 완료 여부를 다시 본다. 같은 계정의 확정이 동시에 와도 하나만 저장한다. */
	private Result save(Long userId, OnboardingCompleteRequest request) {
		User user = userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
		Optional<Result> done = findCompleted(user);
		if (done.isPresent()) {
			return done.get();
		}

		assertInterestsExist(request.interestIds());
		assertQuestionsExist(request.answers());
		String nickname = request.nickname();
		// 맡은 뒤에 DB를 본다. 이 사이 다른 계정은 이 닉네임을 맡지 못하므로, 확인에 성공한 다른 계정의 닉네임을 저장하는 일이 없다.
		nicknameService.holdForSave(userId, nickname);
		if (userProfileRepository.existsByNicknameAndUserIdNot(nickname, userId)) {
			throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
		}
		Long termId = termService.findCurrentTermId()
				.orElseThrow(() -> new IllegalStateException("열린 학기가 없어 땅굴 입구를 놓을 수 없습니다."));

		UserProfile profile = userProfileRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("프로필이 없는 계정: " + userId));
		OnboardingCompleteRequest.Mole mole = request.mole();
		profile.completeOnboarding(nickname, mole.head(), mole.face(), mole.neck(), mole.hand());
		saveNickname();

		List<Long> interestIds = request.interestIds();
		List<UserInterest> interests = new ArrayList<>(interestIds.size());
		for (int i = 0; i < interestIds.size(); i++) {
			interests.add(UserInterest.create(userId, interestIds.get(i), i + 1));
		}
		userInterestRepository.saveAll(interests);

		List<OnboardingCompleteRequest.Answer> answers = request.answers();
		List<UserAnswer> userAnswers = new ArrayList<>(answers.size());
		for (int i = 0; i < answers.size(); i++) {
			OnboardingCompleteRequest.Answer answer = answers.get(i);
			userAnswers.add(UserAnswer.create(userId, i + 1, answer.introQuestionId(), answer.answer()));
		}
		userAnswerRepository.saveAll(userAnswers);

		Long burrowId = burrowService.createBurrow(userId, termId, profile.getCampusFloorId());
		user.completeOnboarding(Instant.now(clock));
		return new Result(new OnboardingCompleteResponse(userId, NextStep.HOME, burrowId), true);
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

	private User findActiveUser(Long userId) {
		return userRepository.findById(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
	}

	private Optional<Result> findCompleted(User user) {
		if (!user.isOnboarded()) {
			return Optional.empty();
		}
		Burrow burrow = burrowRepository.findByUserId(user.getId())
				.orElseThrow(() -> new IllegalStateException("온보딩을 마쳤는데 땅굴이 없는 계정: " + user.getId()));
		return Optional.of(new Result(new OnboardingCompleteResponse(user.getId(), NextStep.HOME, burrow.getId()),
				false));
	}

	/** Bean Validation과 세 질문이 서로 다른지를 보고, 어긋난 것을 {@code fields}에 모두 담는다. */
	private void validate(OnboardingCompleteRequest request) {
		Errors errors = new BeanPropertyBindingResult(request, "request");
		validator.validate(request, errors);
		List<ApiError.FieldError> fields = new ArrayList<>();
		errors.getFieldErrors().forEach(error -> fields.add(new ApiError.FieldError(error.getField(),
				error.getDefaultMessage())));
		if (request.answers() != null && request.answers().stream().allMatch(answer -> answer != null
				&& answer.introQuestionId() != null)) {
			long distinct = request.answers().stream().map(OnboardingCompleteRequest.Answer::introQuestionId)
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

	/** 모두 지우지 않은 세부 관심사인지. 같은 id가 두 번 오지 않는 것은 입력 검사에서 봤다. */
	private void assertInterestsExist(List<Long> interestIds) {
		long found = interestRepository.findAllById(interestIds).stream()
				.filter(interest -> interest.getDeletedAt() == null)
				.count();
		if (found != interestIds.size()) {
			throw new BusinessException(InterestErrorCode.INTEREST_NOT_FOUND);
		}
	}

	private void assertQuestionsExist(List<OnboardingCompleteRequest.Answer> answers) {
		HashSet<Long> ids = new HashSet<>();
		answers.forEach(answer -> ids.add(answer.introQuestionId()));
		if (introQuestionRepository.findAllById(ids).size() != ids.size()) {
			throw new BusinessException(IntroQuestionErrorCode.INTRO_QUESTION_NOT_FOUND);
		}
	}

	/**
	 * @param response 응답의 {@code data}
	 * @param created 이번에 처음 저장했으면 true({@code 201}), 이미 마친 계정이면 false({@code 200})
	 */
	public record Result(OnboardingCompleteResponse response, boolean created) {
	}

}
