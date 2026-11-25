package com.itting.auth.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.auth.domain.GoogleOAuthState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Google로 보낸 시도를 Redis의 {@code itting:oauth:google:<state>}에 둔다. 값은 하나의 글이다.
 * 콜백은 {@link #consume}으로 읽으면서 바로 지워(GETDEL) 같은 {@code state}를 두 번 쓸 수 없다. 동시에 두 번 불러도
 * 하나만 받는다. 만료는 저장한 시각과 서버 시계로 판단하고, Redis의 만료는 청소용이다.
 */
@Repository
public class GoogleOAuthStateRepository {

	private static final String KEY = "itting:oauth:google:";

	/** 값의 칸을 가르는 글자. 각 칸은 enum 이름, 숫자, 세션 id(UUID), base64url이라 이 글자가 들어 있지 않다. */
	private static final String SEPARATOR = "|";

	private final StringRedisTemplate redis;

	public GoogleOAuthStateRepository(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public void save(String state, GoogleOAuthState record, Duration ttl) {
		String value = String.join(SEPARATOR,
				record.purpose().name(),
				record.userId() == null ? "" : String.valueOf(record.userId()),
				record.sessionId() == null ? "" : record.sessionId(),
				record.codeVerifier(),
				String.valueOf(record.expiresAt().toEpochMilli()));
		redis.opsForValue().set(KEY + state, value, ttl);
	}

	/** 시도를 읽고 바로 지운다. 없거나(이미 썼거나 만료) 읽을 수 없으면 empty. */
	public Optional<GoogleOAuthState> consume(String state) {
		String value = redis.opsForValue().getAndDelete(KEY + state);
		if (value == null) {
			return Optional.empty();
		}
		String[] parts = value.split("\\|", -1);
		if (parts.length != 5) {
			return Optional.empty();
		}
		try {
			return Optional.of(new GoogleOAuthState(
					GoogleOAuthPurpose.valueOf(parts[0]),
					parts[1].isEmpty() ? null : Long.valueOf(parts[1]),
					parts[2].isEmpty() ? null : parts[2],
					parts[3],
					Instant.ofEpochMilli(Long.parseLong(parts[4]))));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

}
