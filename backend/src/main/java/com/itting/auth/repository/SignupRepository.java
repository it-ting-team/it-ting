package com.itting.auth.repository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.itting.auth.domain.SignupMethod;
import com.itting.auth.domain.SignupRecord;
import com.itting.auth.domain.VerificationCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 가입 진행 기록을 Redis의 {@code itting:signup:<토큰>}에 둔다. 토큰은 가입 쿠키의 값이다.
 * 만료는 저장한 시각과 서버 시계로 판단하고, Redis의 만료는 청소용이다.
 */
@Repository
public class SignupRepository {

	private static final String KEY = "itting:signup:";

	private static final String SSAFY_CODE = "ssafyCode";

	private static final String SSAFY_EXPIRES_AT = "ssafyExpiresAt";

	private static final String SSAFY_ATTEMPTS = "ssafyAttempts";

	private static final String SSAFY_SENT_AT = "ssafySentAt";

	private final StringRedisTemplate redis;

	private final SecureRandom random = new SecureRandom();

	public SignupRepository(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** 이메일 가입 기록을 만들고 토큰을 준다. 토큰은 추측할 수 없는 무작위 32바이트다. */
	public String createEmailSignup(String email, Instant now, Duration ttl) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		String key = KEY + token;
		redis.opsForHash().putAll(key, Map.of(
				"method", SignupMethod.EMAIL.name(),
				"email", email,
				"expiresAt", millis(now.plus(ttl))));
		redis.expire(key, ttl);
		return token;
	}

	public Optional<SignupRecord> find(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		Map<Object, Object> hash = redis.opsForHash().entries(KEY + token);
		// 기록이 Redis에서 사라진 직후에 다른 값만 쓰인 경우(가입 방법이 없음)도 없는 기록으로 본다.
		if (!hash.containsKey("method")) {
			return Optional.empty();
		}
		VerificationCode ssafyCode = hash.containsKey(SSAFY_CODE)
				? new VerificationCode((String) hash.get(SSAFY_CODE), instant(hash.get(SSAFY_EXPIRES_AT)),
						Integer.parseInt((String) hash.get(SSAFY_ATTEMPTS)))
				: null;
		return Optional.of(new SignupRecord(
				token,
				SignupMethod.valueOf((String) hash.get("method")),
				(String) hash.get("email"),
				instant(hash.get("expiresAt")),
				hash.containsKey("generation") ? Integer.valueOf((String) hash.get("generation")) : null,
				(String) hash.get("mattermostId"),
				ssafyCode,
				hash.containsKey(SSAFY_SENT_AT) ? instant(hash.get(SSAFY_SENT_AT)) : null));
	}

	/**
	 * SSAFY 인증 코드를 보낼 자리를 잡는다. 동시에 여러 요청이 와도 하나만 잡는다.
	 *
	 * @return 잡았으면 {@code RESERVED}, 간격이 지나지 않았으면 {@code TOO_SOON}, 가입 기록이 사라졌으면 {@code NO_RECORD}
	 */
	public SendReservation reserveSsafySend(String token, Instant now, Duration interval) {
		Long result = redis.execute(VerificationScripts.RESERVE_SEND, List.of(KEY + token),
				millis(now), String.valueOf(interval.toMillis()), SSAFY_SENT_AT, "method");
		if (result == null || result == -1L) {
			return SendReservation.NO_RECORD;
		}
		return result == 1L ? SendReservation.RESERVED : SendReservation.TOO_SOON;
	}

	public enum SendReservation {

		RESERVED,

		TOO_SOON,

		NO_RECORD

	}

	/** SSAFY 인증 코드를 저장한다. 이전 코드와 틀린 횟수는 덮어쓴다. 보낸 시각은 {@link #reserveSsafySend}가 적었다. */
	public void saveSsafyCode(String token, int generation, String mattermostId, String code, Instant expiresAt) {
		Map<String, String> values = new HashMap<>();
		values.put("generation", String.valueOf(generation));
		values.put("mattermostId", mattermostId);
		values.put(SSAFY_CODE, code);
		values.put(SSAFY_EXPIRES_AT, millis(expiresAt));
		values.put(SSAFY_ATTEMPTS, "0");
		redis.opsForHash().putAll(KEY + token, values);
	}

	/**
	 * SSAFY 인증 코드를 확인한다. 틀린 횟수가 최대에 닿으면 코드를 지운다(재전송 간격은 그대로 센다).
	 * 맞아도 지우지 않는다. 계정을 만들다 실패하면(겹침, 없는 층) 같은 코드로 다시 시도할 수 있다.
	 */
	public VerificationCode.Result verifySsafyCode(String token, String input, Instant now, int maxAttempts) {
		String result = redis.execute(VerificationScripts.VERIFY, List.of(KEY + token),
				input, millis(now), String.valueOf(maxAttempts), SSAFY_CODE, SSAFY_EXPIRES_AT, SSAFY_ATTEMPTS, "0");
		return VerificationScripts.toResult(result);
	}

	/** 보내지 못했을 때 코드와 보낸 시각을 모두 지워 바로 다시 보낼 수 있게 한다. */
	public void deleteSsafyCodeAndSentAt(String token) {
		redis.opsForHash().delete(KEY + token, SSAFY_CODE, SSAFY_EXPIRES_AT, SSAFY_ATTEMPTS, SSAFY_SENT_AT);
	}

	public void delete(String token) {
		if (token != null && !token.isBlank()) {
			redis.delete(KEY + token);
		}
	}

	private static String millis(Instant instant) {
		return String.valueOf(instant.toEpochMilli());
	}

	private static Instant instant(Object millis) {
		return Instant.ofEpochMilli(Long.parseLong((String) millis));
	}

}
