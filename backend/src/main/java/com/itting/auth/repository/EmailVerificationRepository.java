package com.itting.auth.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.itting.auth.domain.VerificationCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 이메일 인증 코드를 Redis에 둔다. 이메일은 소문자로 바꿔 키로 쓴다.
 *
 * <ul>
 * <li>{@code itting:email-code:<이메일>}: 코드, 만료 시각, 틀린 횟수. 유효 시간이 지나면 저절로 사라진다.</li>
 * <li>{@code itting:email-code-sent:<이메일>}: 마지막으로 보낸 시각(재전송 간격). 코드를 지워도 남는다.</li>
 * </ul>
 *
 * <p>만료와 재전송 간격은 Redis의 만료가 아니라 저장한 시각과 서버 시계로 판단한다. Redis의 만료는 청소용이다.
 */
@Repository
public class EmailVerificationRepository {

	private static final String CODE_KEY = "itting:email-code:";

	private static final String SENT_KEY = "itting:email-code-sent:";

	private final StringRedisTemplate redis;

	public EmailVerificationRepository(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public Optional<Instant> findLastSentAt(String email) {
		String value = redis.opsForValue().get(SENT_KEY + email);
		return Optional.ofNullable(value).map(millis -> Instant.ofEpochMilli(Long.parseLong(millis)));
	}

	/** 새 코드를 저장한다. 이 이메일의 이전 코드와 틀린 횟수는 덮어쓴다. */
	public void save(String email, String code, Instant sentAt, Instant expiresAt, Duration resendInterval) {
		String key = CODE_KEY + email;
		redis.delete(key);
		redis.opsForHash().putAll(key, Map.of(
				"code", code,
				"expiresAt", String.valueOf(expiresAt.toEpochMilli()),
				"attempts", "0"));
		redis.expire(key, Duration.between(sentAt, expiresAt));
		redis.opsForValue().set(SENT_KEY + email, String.valueOf(sentAt.toEpochMilli()), resendInterval);
	}

	public Optional<VerificationCode> findCode(String email) {
		Map<Object, Object> hash = redis.opsForHash().entries(CODE_KEY + email);
		if (hash.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new VerificationCode(
				(String) hash.get("code"),
				Instant.ofEpochMilli(Long.parseLong((String) hash.get("expiresAt"))),
				Integer.parseInt((String) hash.get("attempts"))));
	}

	/** @return 늘린 뒤의 틀린 횟수 */
	public long incrementAttempts(String email) {
		return redis.opsForHash().increment(CODE_KEY + email, "attempts", 1);
	}

	/** 코드만 지운다. 재전송 간격은 그대로 센다. */
	public void deleteCode(String email) {
		redis.delete(CODE_KEY + email);
	}

	/** 보내지 못했을 때 코드와 보낸 시각을 모두 지워 바로 다시 보낼 수 있게 한다. */
	public void deleteAll(String email) {
		redis.delete(CODE_KEY + email);
		redis.delete(SENT_KEY + email);
	}

}
