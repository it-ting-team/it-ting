package com.itting.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.domain.VerificationCode;
import com.itting.auth.repository.EmailVerificationRepository;
import com.itting.auth.repository.SignupRepository;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.user.repository.UserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * 회원가입 1의 이메일 인증({@code POST /api/v1/email-verifications}, {@code /check}).
 * 코드를 맞히면 가입 진행 기록을 만든다. DB에는 아무것도 저장하지 않는다.
 */
@Service
@EnableConfigurationProperties(VerificationProperties.class)
public class EmailVerificationService {

	private final EmailVerificationRepository emailVerificationRepository;

	private final SignupRepository signupRepository;

	private final UserRepository userRepository;

	private final VerificationCodeGenerator codeGenerator;

	private final VerificationMailService mailService;

	private final VerificationProperties properties;

	private final Clock clock;

	public EmailVerificationService(EmailVerificationRepository emailVerificationRepository,
			SignupRepository signupRepository, UserRepository userRepository, VerificationCodeGenerator codeGenerator,
			VerificationMailService mailService, VerificationProperties properties, Clock clock) {
		this.emailVerificationRepository = emailVerificationRepository;
		this.signupRepository = signupRepository;
		this.userRepository = userRepository;
		this.codeGenerator = codeGenerator;
		this.mailService = mailService;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * 코드를 만들어 메일로 보낸다. 이 이메일의 이전 코드는 쓸 수 없게 된다.
	 *
	 * @return 코드가 만료되는 시각
	 */
	public Instant send(String email) {
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(AuthErrorCode.EMAIL_DUPLICATED);
		}
		String key = key(email);
		Instant now = Instant.now(clock);
		emailVerificationRepository.findLastSentAt(key)
				.filter(sentAt -> now.isBefore(sentAt.plus(properties.resendInterval())))
				.ifPresent(sentAt -> {
					throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
				});

		String code = codeGenerator.generate();
		Instant expiresAt = now.plus(properties.codeTtl());
		emailVerificationRepository.save(key, code, now, expiresAt, properties.resendInterval());
		try {
			mailService.sendCode(email, code, properties.codeTtl());
		}
		catch (BusinessException e) {
			emailVerificationRepository.deleteAll(key);
			throw e;
		}
		return expiresAt;
	}

	/**
	 * 코드를 확인하고 가입 진행 기록을 만든다. 맞힌 코드는 다시 쓸 수 없다.
	 *
	 * @param previousToken 이 브라우저의 이전 가입 진행 기록. 있으면 지운다
	 * @return 새 가입 진행 기록의 토큰(가입 쿠키의 값)
	 */
	public String check(String email, String code, String previousToken) {
		String key = key(email);
		Instant now = Instant.now(clock);
		VerificationCode stored = emailVerificationRepository.findCode(key)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.VERIFICATION_CODE_EXPIRED));
		switch (stored.check(code, now)) {
			case EXPIRED -> throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
			case MISMATCH -> {
				if (emailVerificationRepository.incrementAttempts(key) >= properties.maxAttempts()) {
					emailVerificationRepository.deleteCode(key);
				}
				throw new BusinessException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
			}
			case MATCH -> emailVerificationRepository.deleteCode(key);
		}
		signupRepository.delete(previousToken);
		return signupRepository.createEmailSignup(email, now, properties.signupTtl());
	}

	/** 이메일은 대소문자를 가리지 않는다(DB의 비교와 같게). */
	private static String key(String email) {
		return email.toLowerCase(Locale.ROOT);
	}

}
