package com.itting.auth.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
 * <p>확인과 재전송 간격은 {@link VerificationScripts}로 Redis 안에서 한 번에 처리해 동시 요청에도 어긋나지 않는다.
 * 만료와 간격은 저장한 시각과 서버 시계로 판단하고, Redis의 만료는 청소용이다.
 */
@Repository
public class EmailVerificationRepository {

	private static final String CODE_KEY = "itting:email-code:";

	private static final String SENT_KEY = "itting:email-code-sent:";

	private final StringRedisTemplate redis;

	public EmailVerificationRepository(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/**
	 * 보낼 자리를 잡는다. 동시에 여러 요청이 와도 하나만 잡는다.
	 *
	 * @return 마지막으로 보낸 뒤 간격이 지나지 않았으면 false
	 */
	public boolean reserveSend(String email, Instant now, Duration interval) {
		Long result = redis.execute(VerificationScripts.RESERVE_SEND, List.of(SENT_KEY + email),
				millis(now), String.valueOf(interval.toMillis()), "", "");
		return result != null && result == 1L;
	}

	/** 새 코드를 저장한다. 이 이메일의 이전 코드와 틀린 횟수는 덮어쓴다. */
	public void saveCode(String email, String code, Instant now, Instant expiresAt) {
		String key = CODE_KEY + email;
		redis.delete(key);
		redis.opsForHash().putAll(key, Map.of(
				"code", code,
				"expiresAt", millis(expiresAt),
				"attempts", "0"));
		redis.expire(key, Duration.between(now, expiresAt));
	}

	/** 코드를 확인한다. 맞으면 지우고, 틀린 횟수가 최대에 닿으면 지운다. 재전송 간격은 그대로 센다. */
	public VerificationCode.Result verify(String email, String input, Instant now, int maxAttempts) {
		List<?> result = redis.execute(VerificationScripts.VERIFY, List.of(CODE_KEY + email),
				input, millis(now), String.valueOf(maxAttempts), "code", "expiresAt", "attempts", "1", "", "");
		return VerificationScripts.toResult(result);
	}

	/** 보내지 못했을 때 코드와 보낸 시각을 모두 지워 바로 다시 보낼 수 있게 한다. */
	public void deleteAll(String email) {
		redis.delete(List.of(CODE_KEY + email, SENT_KEY + email));
	}

	private static String millis(Instant instant) {
		return String.valueOf(instant.toEpochMilli());
	}

}
