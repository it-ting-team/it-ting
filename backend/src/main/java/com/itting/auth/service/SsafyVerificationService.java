package com.itting.auth.service;

import java.time.Clock;
import java.time.Instant;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.repository.SignupRepository;
import com.itting.generation.service.GenerationService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.user.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * 회원가입 3의 SSAFY 인증 코드 보내기({@code POST /api/v1/ssafy-verifications}).
 * 코드는 가입 진행 기록에 두고, 계정을 만들 때 확인한다.
 */
@Service
public class SsafyVerificationService {

	private final SignupRepository signupRepository;

	private final UserRepository userRepository;

	private final GenerationService generationService;

	private final VerificationCodeGenerator codeGenerator;

	private final MattermostService mattermostService;

	private final VerificationProperties properties;

	private final Clock clock;

	public SsafyVerificationService(SignupRepository signupRepository, UserRepository userRepository,
			GenerationService generationService, VerificationCodeGenerator codeGenerator,
			MattermostService mattermostService, VerificationProperties properties, Clock clock) {
		this.signupRepository = signupRepository;
		this.userRepository = userRepository;
		this.generationService = generationService;
		this.codeGenerator = codeGenerator;
		this.mattermostService = mattermostService;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param mattermostId 소문자로 바꾸고 앞의 {@code @}를 뗀 값
	 * @return 코드가 만료되는 시각
	 */
	public Instant send(String signupToken, int generation, String mattermostId) {
		Instant now = Instant.now(clock);
		signupRepository.find(signupToken)
				.filter(found -> !found.isExpired(now))
				.orElseThrow(() -> new BusinessException(AuthErrorCode.SIGNUP_EXPIRED));
		if (!generationService.isAvailable(generation)) {
			throw BusinessException.invalidInput("generation", "고를 수 있는 기수가 아닙니다.");
		}
		if (userRepository.existsByMattermostId(mattermostId)) {
			throw new BusinessException(AuthErrorCode.MATTERMOST_ID_DUPLICATED);
		}
		switch (signupRepository.reserveSsafySend(signupToken, now, properties.resendInterval())) {
			case NO_RECORD -> throw new BusinessException(AuthErrorCode.SIGNUP_EXPIRED);
			case TOO_SOON -> throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
			case RESERVED -> {
			}
		}

		String code = codeGenerator.generate();
		Instant expiresAt = now.plus(properties.codeTtl());
		signupRepository.saveSsafyCode(signupToken, generation, mattermostId, code, expiresAt);
		try {
			mattermostService.sendDirectMessage(generation, mattermostId,
					"[디굴디굴] SSAFY 인증 코드는 %s입니다. %d분 안에 입력해 주세요."
							.formatted(code, properties.codeTtl().toMinutes()));
		}
		catch (BusinessException e) {
			signupRepository.deleteSsafyCodeAndSentAt(signupToken);
			throw e;
		}
		return expiresAt;
	}

}
