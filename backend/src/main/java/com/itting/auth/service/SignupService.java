package com.itting.auth.service;

import java.time.Clock;
import java.time.Instant;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.domain.SignupMethod;
import com.itting.auth.domain.SignupRecord;
import com.itting.auth.repository.SignupRepository;
import com.itting.campus.domain.CampusErrorCode;
import com.itting.campus.repository.CampusFloorRepository;
import com.itting.global.error.BusinessException;
import com.itting.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 회원가입 3의 "땅굴 파기"({@code POST /api/v1/users}). SSAFY 인증 코드를 확인하고 계정을 만든다.
 * 계정과 프로필은 {@link UserService}가 한 트랜잭션으로 만들고, 그 뒤에 가입 진행 기록을 지운다.
 */
@Service
public class SignupService {

	private final SignupRepository signupRepository;

	private final CampusFloorRepository campusFloorRepository;

	private final UserService userService;

	private final PasswordEncoder passwordEncoder;

	private final VerificationProperties properties;

	private final Clock clock;

	public SignupService(SignupRepository signupRepository, CampusFloorRepository campusFloorRepository,
			UserService userService, PasswordEncoder passwordEncoder, VerificationProperties properties, Clock clock) {
		this.signupRepository = signupRepository;
		this.campusFloorRepository = campusFloorRepository;
		this.userService = userService;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param password 이메일 가입이면 필수. 규칙 검사는 요청 DTO가 했다
	 * @return 만든 계정의 id
	 */
	public Long signUp(String signupToken, String password, String code, Long campusFloorId) {
		Instant now = Instant.now(clock);
		SignupRecord record = signupRepository.find(signupToken)
				.filter(found -> !found.isExpired(now))
				.orElseThrow(() -> new BusinessException(AuthErrorCode.SIGNUP_EXPIRED));
		if (record.method() != SignupMethod.EMAIL) {
			// Google 가입은 02 ④에서 만든다. 그 전에는 Google 가입 기록이 생기지 않는다.
			throw new IllegalStateException("Google 가입은 아직 처리하지 않는다: " + record.method());
		}
		if (password == null) {
			throw BusinessException.invalidInput("password", "필수 값입니다.");
		}
		verifySsafyCode(record, code, now);
		boolean floorExists = campusFloorRepository.findById(campusFloorId)
				.filter(floor -> floor.getDeletedAt() == null)
				.isPresent();
		if (!floorExists) {
			throw new BusinessException(CampusErrorCode.CAMPUS_FLOOR_NOT_FOUND);
		}

		Long userId = userService.createEmailUser(record.email(), passwordEncoder.encode(password),
				record.mattermostId(), record.generation(), campusFloorId, now);
		signupRepository.delete(signupToken);
		return userId;
	}

	private void verifySsafyCode(SignupRecord record, String code, Instant now) {
		switch (signupRepository.verifySsafyCode(record.token(), code, now, properties.maxAttempts())) {
			case EXPIRED -> throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
			case MISMATCH -> throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
			case MATCH -> {
			}
		}
	}

}
