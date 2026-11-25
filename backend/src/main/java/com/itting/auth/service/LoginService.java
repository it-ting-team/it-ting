package com.itting.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.domain.NextStep;
import com.itting.auth.dto.SuspensionDetailResponse;
import com.itting.global.error.BusinessException;
import com.itting.user.domain.User;
import com.itting.user.domain.UserRole;
import com.itting.user.domain.UserSuspension;
import com.itting.user.repository.UserRepository;
import com.itting.user.repository.UserSuspensionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일과 비밀번호로 계정을 확인한다({@code POST /api/v1/sessions}). 세션은 {@link SessionService}가 만든다.
 */
@Service
public class LoginService {

	/** BCrypt가 보는 비밀번호의 최대 길이. 넘는 값은 비교하지 않고 틀린 것으로 본다. */
	private static final int BCRYPT_MAX_BYTES = 72;

	private final UserRepository userRepository;

	private final UserSuspensionRepository userSuspensionRepository;

	private final PasswordEncoder passwordEncoder;

	private final NextStepService nextStepService;

	private final Clock clock;

	/** 계정이 없을 때도 BCrypt 비교를 한 번 하려고 쓰는 해시. 응답 시간으로 가입 여부를 알 수 없게 한다. */
	private final String dummyHash;

	public LoginService(UserRepository userRepository, UserSuspensionRepository userSuspensionRepository,
			PasswordEncoder passwordEncoder, NextStepService nextStepService, Clock clock) {
		this.userRepository = userRepository;
		this.userSuspensionRepository = userSuspensionRepository;
		this.passwordEncoder = passwordEncoder;
		this.nextStepService = nextStepService;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("it-ting-dummy-password");
	}

	/**
	 * @throws BusinessException 계정이 없거나 비밀번호가 다르면 {@code LOGIN_FAILED},
	 *         비밀번호가 맞고 정지 중이면 {@code ACCOUNT_SUSPENDED}
	 */
	@Transactional(readOnly = true)
	public LoginResult login(String email, String password) {
		User user = userRepository.findByEmail(email).filter(User::isActive).orElse(null);
		String passwordHash = user != null ? user.getPasswordHash() : null;
		if (!matches(password, passwordHash)) {
			throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
		}

		// 비밀번호가 맞은 뒤에만 정지 사실을 알린다. 비밀번호를 모르는 사람은 남의 정지 여부를 알 수 없다.
		findSuspension(user.getId()).ifPresent(detail -> {
			throw new BusinessException(AuthErrorCode.ACCOUNT_SUSPENDED, detail);
		});
		return new LoginResult(user.getId(), user.getRole(), nextStepService.of(user));
	}

	/**
	 * 지금 정지 중이면 정지 사유와 끝나는 시각. 정지 기록이 겹치면 가장 늦게 끝나는 것이다. Google 로그인도 이것으로 정지를 본다.
	 */
	@Transactional(readOnly = true)
	public Optional<SuspensionDetailResponse> findSuspension(Long userId) {
		Instant now = Instant.now(clock);
		return userSuspensionRepository.findActive(userId, now).stream().findFirst().map(LoginService::toDetail);
	}

	/** 계정이 없거나 비밀번호가 없는 계정(Google로만 가입)이어도 같은 시간이 걸리게 비교한다. */
	private boolean matches(String password, String passwordHash) {
		boolean tooLong = password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
		String candidate = tooLong ? "" : password;
		if (passwordHash == null) {
			passwordEncoder.matches(candidate, dummyHash);
			return false;
		}
		return passwordEncoder.matches(candidate, passwordHash) && !tooLong;
	}

	private static SuspensionDetailResponse toDetail(UserSuspension suspension) {
		return new SuspensionDetailResponse(suspension.getReason(), suspension.getEndsAt());
	}

	/**
	 * @param userId 로그인한 계정
	 * @param role 세션에 넣을 역할
	 * @param nextStep 다음에 갈 화면
	 */
	public record LoginResult(Long userId, UserRole role, NextStep nextStep) {
	}

}
