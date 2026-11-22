package com.itting.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.repository.PasswordResetRepository;
import com.itting.auth.repository.PasswordResetRepository.PasswordResetLink;
import com.itting.global.config.FrontendProperties;
import com.itting.global.error.BusinessException;
import com.itting.user.domain.User;
import com.itting.user.repository.UserRepository;
import com.itting.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 비밀번호 재설정({@code /api/v1/password-resets}). 가입 여부가 드러나지 않게 한다.
 *
 * <ul>
 * <li>어떤 이메일이든 같은 응답이다. 계정이 없거나 비밀번호가 없는 계정(Google로만 가입)이면 아무것도 하지 않는다.</li>
 * <li>같은 이메일로 간격 안에 다시 요청하면 메일만 보내지 않는다. {@code 429}로 답하면 가입한 이메일만 걸려 드러나기 때문이다.</li>
 * <li>메일은 응답한 뒤 따로 보낸다. 가입한 이메일일 때만 응답이 늦어지면 응답 시간으로 드러나기 때문이다.</li>
 * </ul>
 */
@Service
@EnableConfigurationProperties(PasswordResetService.PasswordResetProperties.class)
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

	private final PasswordResetRepository passwordResetRepository;

	private final UserRepository userRepository;

	private final UserService userService;

	private final SessionService sessionService;

	private final VerificationMailService mailService;

	private final PasswordEncoder passwordEncoder;

	/** 메일을 응답과 따로 보내는 실행기. 보낼 때마다 가상 스레드 하나를 쓴다. */
	private final TaskExecutor taskExecutor;

	private final PasswordResetProperties properties;

	private final FrontendProperties frontendProperties;

	private final Clock clock;

	public PasswordResetService(PasswordResetRepository passwordResetRepository, UserRepository userRepository,
			UserService userService, SessionService sessionService, VerificationMailService mailService,
			PasswordEncoder passwordEncoder, PasswordResetProperties properties,
			FrontendProperties frontendProperties, Clock clock) {
		this.passwordResetRepository = passwordResetRepository;
		this.userRepository = userRepository;
		this.userService = userService;
		this.sessionService = sessionService;
		this.mailService = mailService;
		this.passwordEncoder = passwordEncoder;
		SimpleAsyncTaskExecutor mailExecutor = new SimpleAsyncTaskExecutor("password-reset-mail-");
		mailExecutor.setVirtualThreads(true);
		this.taskExecutor = mailExecutor;
		this.properties = properties;
		this.frontendProperties = frontendProperties;
		this.clock = clock;
	}

	/** 재설정 메일을 보낸다. 보냈는지는 알리지 않는다. */
	public void request(String email) {
		Instant now = Instant.now(clock);
		if (!passwordResetRepository.reserveRequest(email.toLowerCase(Locale.ROOT), now, properties.requestInterval())) {
			return;
		}
		User user = userRepository.findByEmail(email)
				.filter(User::isActive)
				.filter(found -> found.getPasswordHash() != null)
				.orElse(null);
		if (user == null) {
			return;
		}
		// DB는 이메일의 대소문자와 악센트를 가리지 않아 다른 입력이 같은 계정이 된다. 계정으로도 간격을 센다.
		if (!passwordResetRepository.reserveRequestForUser(user.getId(), now, properties.requestInterval())) {
			return;
		}
		String token = passwordResetRepository.issue(user.getId(), now, properties.linkTtl());
		String link = frontendProperties.frontendUrl() + "/password-reset/" + token;
		String to = user.getEmail();
		taskExecutor.execute(() -> {
			try {
				mailService.sendPasswordReset(to, link, properties.linkTtl());
			}
			catch (BusinessException e) {
				// 받는 사람과 링크는 남기지 않는다. 메일 서비스가 실패 종류를 이미 남겼다.
				log.warn("비밀번호 재설정 메일을 보내지 못했습니다.");
			}
		});
	}

	/** 링크가 유효한지 본다. 지우지 않는다. */
	public void check(String token) {
		passwordResetRepository.find(token)
				.filter(link -> !link.isExpired(Instant.now(clock)))
				.orElseThrow(() -> new BusinessException(AuthErrorCode.PASSWORD_RESET_LINK_INVALID));
	}

	/**
	 * 새 비밀번호를 저장하고 링크를 지운다. 그 계정의 로그인 세션을 모두 지운다. 로그인시키지 않는다.
	 *
	 * @param password 규칙 검사는 요청 DTO가 했다
	 */
	public void complete(String token, String password) {
		PasswordResetLink link = passwordResetRepository.consume(token)
				.filter(found -> !found.isExpired(Instant.now(clock)))
				.orElseThrow(() -> new BusinessException(AuthErrorCode.PASSWORD_RESET_LINK_INVALID));
		if (!userService.changePassword(link.userId(), passwordEncoder.encode(password))) {
			throw new BusinessException(AuthErrorCode.PASSWORD_RESET_LINK_INVALID);
		}
		sessionService.endAll(link.userId());
	}

	/**
	 * @param linkTtl 링크를 쓸 수 있는 시간
	 * @param requestInterval 같은 이메일로 다시 메일을 보내기까지의 간격
	 */
	@ConfigurationProperties("itting.password-reset")
	public record PasswordResetProperties(Duration linkTtl, Duration requestInterval) {
	}

}
