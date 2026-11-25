package com.itting.auth.service;

import java.time.Clock;
import java.time.Instant;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.domain.SignupMethod;
import com.itting.auth.domain.SignupRecord;
import com.itting.auth.repository.SignupRepository;
import com.itting.auth.repository.SignupRepository.SsafyCodeCheck;
import com.itting.campus.domain.CampusErrorCode;
import com.itting.campus.repository.CampusFloorRepository;
import com.itting.global.error.BusinessException;
import com.itting.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 회원가입 3의 "땅굴 파기"({@code POST /api/v1/users}). SSAFY 인증 코드를 확인하고 계정을 만든다.
 * 이메일 가입과 Google 가입이 같은 길을 쓰고, 계정을 만드는 곳만 다르다.
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
	 * @param password 이메일 가입이면 필수. Google 가입이면 쓰지 않는다. 규칙 검사는 요청 DTO가 했다
	 * @return 만든 계정의 id
	 */
	public Long signUp(String signupToken, String password, String code, Long campusFloorId) {
		Instant now = Instant.now(clock);
		SignupRecord record = signupRepository.find(signupToken)
				.filter(found -> !found.isExpired(now))
				.orElseThrow(() -> new BusinessException(AuthErrorCode.SIGNUP_EXPIRED));
		if (record.method() == SignupMethod.EMAIL && password == null) {
			throw BusinessException.invalidInput("password", "필수 값입니다.");
		}
		SsafyCodeCheck verified = verifySsafyCode(record, code, now);
		boolean floorExists = campusFloorRepository.findById(campusFloorId)
				.filter(floor -> floor.getDeletedAt() == null)
				.isPresent();
		if (!floorExists) {
			throw new BusinessException(CampusErrorCode.CAMPUS_FLOOR_NOT_FOUND);
		}

		// 기수와 아이디는 코드를 확인한 순간의 값을 쓴다. 그 사이 코드를 다시 보내 기록이 바뀌어도 어긋나지 않는다.
		// Google 가입이면 password는 쓰지 않는다. 보냈어도 규칙 검사(요청 DTO)만 하고 저장하지 않는다.
		Long userId = switch (record.method()) {
			case EMAIL -> userService.createEmailUser(record.email(), passwordEncoder.encode(password),
					verified.mattermostId(), verified.generation(), campusFloorId, now);
			case GOOGLE -> userService.createGoogleUser(record.googleSub(), record.googleEmail(),
					verified.mattermostId(), verified.generation(), campusFloorId, now);
		};
		signupRepository.delete(signupToken);
		return userId;
	}

	private SsafyCodeCheck verifySsafyCode(SignupRecord record, String code, Instant now) {
		SsafyCodeCheck check = signupRepository.verifySsafyCode(record.token(), code, now, properties.maxAttempts());
		switch (check.result()) {
			case EXPIRED -> throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
			case MISMATCH -> throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
			case MATCH -> {
			}
		}
		return check;
	}

}
