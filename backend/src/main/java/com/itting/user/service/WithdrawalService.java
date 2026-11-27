package com.itting.user.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.auth.service.GoogleVerificationService;
import com.itting.auth.service.SessionService;
import com.itting.burrow.service.BurrowWithdrawalCleaner;
import com.itting.chat.service.ChatWithdrawalCleaner;
import com.itting.community.service.CommunityWithdrawalCleaner;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.interest.service.InterestWithdrawalCleaner;
import com.itting.meetup.service.MeetupWithdrawalCleaner;
import com.itting.notification.service.NotificationWithdrawalCleaner;
import com.itting.question.service.QuestionWithdrawalCleaner;
import com.itting.user.domain.User;
import com.itting.user.domain.UserErrorCode;
import com.itting.user.domain.UserWithdrawal;
import com.itting.user.dto.WithdrawRequest;
import com.itting.user.dto.WithdrawalCheckResponse;
import com.itting.user.dto.WithdrawalCheckResponse.HostingMeetupResponse;
import com.itting.user.repository.NicknameHoldRepository;
import com.itting.user.repository.UserProfileRepository;
import com.itting.user.repository.UserRepository;
import com.itting.user.repository.UserWithdrawalRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탈퇴 전 확인과 회원 탈퇴({@code GET /api/v1/me/withdrawal-check}, {@code POST /api/v1/me/withdraw}).
 *
 * <p>탈퇴는 여러 영역의 데이터를 한 트랜잭션으로 정리한다. 순서는 이 클래스가 잡고, 각 영역의 테이블은 그 영역의
 * {@code <영역>WithdrawalCleaner}가 다룬다. 트랜잭션은 맨 먼저 내 {@code user} 줄을 잠근다. 대화 요청 · 수락 · 나가기, Google
 * 연동 · 비밀번호 변경이 같은 줄을 잠그므로, 겹치면 한쪽이 끝난 뒤 다른 쪽이 본다. 잠근 뒤 읽는 것이 그 사이 커밋된 값을 보도록
 * {@code READ_COMMITTED}로 연다(모임 신청 API와 같다).
 */
@Service
public class WithdrawalService {

	/** BCrypt가 보는 비밀번호의 최대 길이. 넘는 값은 비교하지 않고 틀린 것으로 본다(로그인과 같다). */
	private static final int BCRYPT_MAX_BYTES = 72;

	private final UserRepository userRepository;

	private final UserProfileRepository userProfileRepository;

	private final UserWithdrawalRepository withdrawalRepository;

	private final NicknameHoldRepository nicknameHolds;

	private final SessionService sessionService;

	private final GoogleVerificationService googleVerificationService;

	private final PasswordEncoder passwordEncoder;

	private final MeetupWithdrawalCleaner meetups;

	private final ChatWithdrawalCleaner chat;

	private final CommunityWithdrawalCleaner community;

	private final BurrowWithdrawalCleaner burrow;

	private final InterestWithdrawalCleaner interests;

	private final QuestionWithdrawalCleaner questions;

	private final NotificationWithdrawalCleaner notifications;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public WithdrawalService(UserRepository userRepository, UserProfileRepository userProfileRepository,
			UserWithdrawalRepository withdrawalRepository, NicknameHoldRepository nicknameHolds,
			SessionService sessionService, GoogleVerificationService googleVerificationService,
			PasswordEncoder passwordEncoder, MeetupWithdrawalCleaner meetups, ChatWithdrawalCleaner chat,
			CommunityWithdrawalCleaner community, BurrowWithdrawalCleaner burrow, InterestWithdrawalCleaner interests,
			QuestionWithdrawalCleaner questions, NotificationWithdrawalCleaner notifications,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.userRepository = userRepository;
		this.userProfileRepository = userProfileRepository;
		this.withdrawalRepository = withdrawalRepository;
		this.nicknameHolds = nicknameHolds;
		this.sessionService = sessionService;
		this.googleVerificationService = googleVerificationService;
		this.passwordEncoder = passwordEncoder;
		this.meetups = meetups;
		this.chat = chat;
		this.community = community;
		this.burrow = burrow;
		this.interests = interests;
		this.questions = questions;
		this.notifications = notifications;
		this.transaction = new TransactionTemplate(transactionManager);
		this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		this.clock = clock;
	}

	/**
	 * 지금 탈퇴할 수 있는지. 탈퇴를 막는 내가 연 예정 모임과, 비밀번호가 없는 계정이면 이 세션의 Google 확인이 아직 유효한지를 준다.
	 *
	 * @throws BusinessException 계정이 없거나 탈퇴했는데 세션만 남았으면 세션을 지우고 {@code UNAUTHENTICATED}
	 */
	public WithdrawalCheckResponse check(Long userId, HttpServletRequest request) {
		User user = userRepository.findById(userId).filter(User::isActive).orElseThrow(() -> unauthenticated(request));
		List<HostingMeetupResponse> hosting = meetups.findHostingUpcoming(userId, clock.instant()).stream()
				.map(meetup -> new HostingMeetupResponse(meetup.id(), meetup.title(), meetup.startsAt()))
				.toList();
		boolean googleVerified = user.getPasswordHash() == null
				&& googleVerificationService.find(sessionId(request), GoogleOAuthPurpose.WITHDRAW).isPresent();
		return new WithdrawalCheckResponse(hosting.isEmpty(), hosting, googleVerified);
	}

	/**
	 * 탈퇴한다. 본인 확인과 예정 모임 확인을 통과하면 각 영역을 정리하고 계정을 탈퇴 상태로 바꾼다. 하나라도 실패하면 모두 되돌린다.
	 * 커밋한 뒤에 Google 확인 기록과 맡아 둔 닉네임을 지우고, 이 계정의 로그인 세션을 모두 지운다. 세션이 지워지면 실시간 소식의
	 * 연결도 끊긴다({@code EventStreamSessionListener}).
	 *
	 * @throws BusinessException 비밀번호가 있는 계정이 {@code password}를 보내지 않았으면 {@code INVALID_INPUT}, 틀리면
	 *         {@code CURRENT_PASSWORD_MISMATCH}. 비밀번호가 없는 계정에 유효한 Google 확인 기록이 없으면
	 *         {@code GOOGLE_VERIFICATION_REQUIRED}. 내가 연 예정 모임이 있으면 {@code HOSTING_MEETUP_EXISTS}. 계정이 없거나
	 *         탈퇴했는데 세션만 남았으면 세션을 지우고 {@code UNAUTHENTICATED}
	 */
	public void withdraw(Long userId, WithdrawRequest body, HttpServletRequest request) {
		String sessionId = sessionId(request);
		Boolean withdrawn = transaction.execute(status -> withdrawLocked(userId, body, sessionId));
		if (!Boolean.TRUE.equals(withdrawn)) {
			throw unauthenticated(request);
		}
		googleVerificationService.delete(sessionId);
		nicknameHolds.release(userId);
		sessionService.endAll(userId);
		sessionService.end(request);
	}

	/** @return 계정이 없거나 이미 탈퇴했으면 false. 아무것도 바꾸지 않는다 */
	private boolean withdrawLocked(Long userId, WithdrawRequest body, String sessionId) {
		User user = userRepository.findByIdForUpdate(userId).filter(User::isActive).orElse(null);
		if (user == null) {
			return false;
		}
		verifyIdentity(user, body.password(), sessionId);
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		if (!meetups.findHostingUpcoming(userId, now).isEmpty()) {
			throw new BusinessException(UserErrorCode.HOSTING_MEETUP_EXISTS);
		}

		meetups.clean(userId, now);
		chat.clean(userId, now);
		community.clean(userId);
		burrow.clean(userId);
		interests.clean(userId);
		questions.clean(userId);
		userProfileRepository.deleteByUserIdNow(userId);
		// 다른 영역의 정리가 만든 알림(참여 취소 · 빈자리)이 내 받은 알림에 섞이지 않게 마지막에 지운다.
		notifications.clean(userId);

		withdrawalRepository.save(UserWithdrawal.create(userId, body.reason()));
		// 앞의 일괄 삭제가 영속성 컨텍스트를 비웠으므로 잠근 계정을 다시 읽어 바꾼다. 잠금은 트랜잭션이 끝날 때까지 남아 있다.
		User locked = userRepository.findById(userId).orElseThrow();
		locked.withdraw();
		userRepository.saveAndFlush(locked);
		return true;
	}

	private void verifyIdentity(User user, String password, String sessionId) {
		String passwordHash = user.getPasswordHash();
		if (passwordHash == null) {
			if (googleVerificationService.find(sessionId, GoogleOAuthPurpose.WITHDRAW).isEmpty()) {
				throw new BusinessException(AuthErrorCode.GOOGLE_VERIFICATION_REQUIRED);
			}
			return;
		}
		if (password == null || password.isEmpty()) {
			throw BusinessException.invalidInput("password", "필수 값입니다.");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES
				|| !passwordEncoder.matches(password, passwordHash)) {
			throw new BusinessException(UserErrorCode.CURRENT_PASSWORD_MISMATCH);
		}
	}

	private BusinessException unauthenticated(HttpServletRequest request) {
		// 계정이 없거나 탈퇴했는데 세션만 남았다. 세션을 지우고 로그인하지 않은 것으로 답한다.
		sessionService.end(request);
		return new BusinessException(CommonErrorCode.UNAUTHENTICATED);
	}

	private static String sessionId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return session == null ? null : session.getId();
	}

}
