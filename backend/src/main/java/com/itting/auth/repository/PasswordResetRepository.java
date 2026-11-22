package com.itting.auth.repository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * 비밀번호 재설정 링크를 Redis에 둔다.
 *
 * <ul>
 * <li>{@code itting:password-reset:<token>}: {@code <계정 id>:<만료 밀리초>}. 30분 뒤 저절로 사라진다.</li>
 * <li>{@code itting:password-reset-user:<계정 id>}: 그 계정의 마지막 링크. 다시 보내면 이전 링크를 지우는 데 쓴다.</li>
 * <li>{@code itting:password-reset-sent:<이메일>}: 마지막으로 요청한 시각(요청 간격). 가입하지 않은 이메일에도 남긴다.</li>
 * </ul>
 *
 * <p>만들기와 쓰기는 Lua 스크립트로 한 번에 해, 동시에 요청해도 링크가 둘 남거나 한 링크를 두 번 쓰지 못한다.
 * 만료는 저장한 시각과 서버 시계로 판단하고, Redis의 만료는 청소용이다.
 */
@Repository
public class PasswordResetRepository {

	private static final String TOKEN_KEY = "itting:password-reset:";

	private static final String USER_KEY = "itting:password-reset-user:";

	private static final String SENT_KEY = "itting:password-reset-sent:";

	/** KEYS: 1 새 링크, 2 계정의 마지막 링크. ARGV: 1 새 토큰, 2 값, 3 만료(밀리초), 4 링크 키 앞부분. */
	private static final RedisScript<Long> ISSUE = new DefaultRedisScript<>("""
			local previous = redis.call('GET', KEYS[2])
			if previous then
			  redis.call('DEL', ARGV[4] .. previous)
			end
			redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
			redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[3])
			return 1
			""", Long.class);

	/** 링크를 읽고 바로 지운다. 계정의 마지막 링크가 이 링크면 그것도 지운다. KEYS: 1 링크. ARGV: 1 토큰, 2 계정 키 앞부분. */
	private static final RedisScript<String> CONSUME = new DefaultRedisScript<>("""
			local value = redis.call('GET', KEYS[1])
			if not value then
			  return ''
			end
			redis.call('DEL', KEYS[1])
			local userId = string.match(value, '^([^:]+)')
			local userKey = ARGV[2] .. userId
			if redis.call('GET', userKey) == ARGV[1] then
			  redis.call('DEL', userKey)
			end
			return value
			""", String.class);

	private final StringRedisTemplate redis;

	private final SecureRandom random = new SecureRandom();

	public PasswordResetRepository(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/**
	 * 요청할 자리를 잡는다. 동시에 여러 요청이 와도 하나만 잡는다.
	 *
	 * @param email 소문자로 바꾼 이메일
	 * @return 마지막 요청 뒤 간격이 지나지 않았으면 false
	 */
	public boolean reserveRequest(String email, Instant now, Duration interval) {
		Long result = redis.execute(VerificationScripts.RESERVE_SEND, List.of(SENT_KEY + email),
				String.valueOf(now.toEpochMilli()), String.valueOf(interval.toMillis()), "", "");
		return result != null && result == 1L;
	}

	/**
	 * 새 링크를 만든다. 그 계정의 이전 링크는 지운다.
	 *
	 * @return 링크에 넣을 값(무작위 32바이트, Base64 URL)
	 */
	public String issue(Long userId, Instant now, Duration ttl) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		String value = userId + ":" + now.plus(ttl).toEpochMilli();
		redis.execute(ISSUE, List.of(TOKEN_KEY + token, USER_KEY + userId),
				token, value, String.valueOf(ttl.toMillis()), TOKEN_KEY);
		return token;
	}

	/** 링크를 읽는다. 지우지 않는다. */
	public Optional<PasswordResetLink> find(String token) {
		return Optional.ofNullable(redis.opsForValue().get(TOKEN_KEY + token)).map(PasswordResetRepository::parse);
	}

	/** 링크를 읽고 지운다. 같은 링크를 두 번 쓸 수 없다. */
	public Optional<PasswordResetLink> consume(String token) {
		String value = redis.execute(CONSUME, List.of(TOKEN_KEY + token), token, USER_KEY);
		return value == null || value.isEmpty() ? Optional.empty() : Optional.of(parse(value));
	}

	private static PasswordResetLink parse(String value) {
		int colon = value.indexOf(':');
		return new PasswordResetLink(Long.valueOf(value.substring(0, colon)),
				Instant.ofEpochMilli(Long.parseLong(value.substring(colon + 1))));
	}

	/**
	 * @param userId 링크를 받은 계정
	 * @param expiresAt 이 시각부터 쓸 수 없다
	 */
	public record PasswordResetLink(Long userId, Instant expiresAt) {

		public boolean isExpired(Instant now) {
			return !now.isBefore(expiresAt);
		}

	}

}
