package com.itting.auth.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.auth.domain.GoogleVerification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Google 확인 기록을 Redis의 {@code itting:google-verification:<세션 id>}에 둔다. 세션마다 하나이고, 다시 확인받으면
 * 덮어쓴다. 만료는 저장한 시각과 서버 시계로 판단하고, Redis의 만료는 청소용이다(판단은 {@code GoogleVerificationService}).
 */
@Repository
public class GoogleVerificationRepository {

	private static final String KEY = "itting:google-verification:";

	private static final String PURPOSE = "purpose";

	private static final String GOOGLE_SUB = "googleSub";

	private static final String GOOGLE_EMAIL = "googleEmail";

	private static final String EXPIRES_AT = "expiresAt";

	private final StringRedisTemplate redis;

	public GoogleVerificationRepository(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** 이 세션의 확인 기록을 만든다. 앞의 기록은 덮어쓴다. */
	public void save(String sessionId, GoogleVerification verification, Duration ttl) {
		String key = KEY + sessionId;
		redis.opsForHash().putAll(key, Map.of(
				PURPOSE, verification.purpose().name(),
				GOOGLE_SUB, verification.googleSub(),
				GOOGLE_EMAIL, verification.googleEmail(),
				EXPIRES_AT, String.valueOf(verification.expiresAt().toEpochMilli())));
		redis.expire(key, ttl);
	}

	public Optional<GoogleVerification> find(String sessionId) {
		Map<Object, Object> hash = redis.opsForHash().entries(KEY + sessionId);
		if (!hash.containsKey(PURPOSE) || !hash.containsKey(GOOGLE_SUB) || !hash.containsKey(GOOGLE_EMAIL)
				|| !hash.containsKey(EXPIRES_AT)) {
			return Optional.empty();
		}
		try {
			return Optional.of(new GoogleVerification(
					GoogleOAuthPurpose.valueOf((String) hash.get(PURPOSE)),
					(String) hash.get(GOOGLE_SUB),
					(String) hash.get(GOOGLE_EMAIL),
					Instant.ofEpochMilli(Long.parseLong((String) hash.get(EXPIRES_AT)))));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public void delete(String sessionId) {
		redis.delete(KEY + sessionId);
	}

}
