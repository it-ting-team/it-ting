package com.itting.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.itting.auth.domain.GoogleAccount;
import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.auth.domain.GoogleVerification;
import com.itting.auth.repository.GoogleVerificationRepository;
import org.springframework.stereotype.Service;

/**
 * Google 확인 기록({@code docs/api/10-settings.md}의 "Google 확인")을 만들고 읽는다. 기록은 로그인 세션에 묶여 있어
 * 확인받은 그 브라우저에서만 쓸 수 있다. 만든 뒤 10분 동안 유효하고, 같은 세션에서 다시 확인받으면 덮어쓰며, 쓰고 나면
 * 지운다.
 *
 * <p>{@code LINK} 기록은 {@code PUT /api/v1/me/google-link}가 쓴다. {@code WITHDRAW} 기록을 읽는 탈퇴 API는 KAN-61이
 * 만들고, 그때 {@link #find}로 읽고 {@link #delete}로 지운다.
 */
@Service
public class GoogleVerificationService {

	/** 확인받은 뒤 쓸 수 있는 시간. */
	public static final Duration TTL = Duration.ofMinutes(10);

	private final GoogleVerificationRepository repository;

	private final Clock clock;

	public GoogleVerificationService(GoogleVerificationRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** 이 세션의 확인 기록을 만든다. 앞의 기록은 덮어쓴다. */
	public void save(String sessionId, GoogleOAuthPurpose purpose, GoogleAccount account) {
		Instant expiresAt = Instant.now(clock).plus(TTL);
		repository.save(sessionId, new GoogleVerification(purpose, account.sub(), account.email(), expiresAt), TTL);
	}

	/** 이 세션의 확인 기록 가운데 이 목적이고 아직 유효한 것. 목적이 다르거나 10분이 지났으면 empty. */
	public Optional<GoogleVerification> find(String sessionId, GoogleOAuthPurpose purpose) {
		if (sessionId == null) {
			return Optional.empty();
		}
		Instant now = Instant.now(clock);
		return repository.find(sessionId)
				.filter(found -> found.purpose() == purpose)
				.filter(found -> !found.isExpired(now));
	}

	/** 이 세션의 확인 기록을 지운다. 쓴 뒤에 부른다. */
	public void delete(String sessionId) {
		if (sessionId != null) {
			repository.delete(sessionId);
		}
	}

}
