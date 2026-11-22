package com.itting.auth.repository;

import java.util.List;

import com.itting.auth.domain.VerificationCode;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 인증 코드와 재전송 간격을 Redis 안에서 한 번에 처리하는 Lua 스크립트.
 * 읽고 판단한 뒤 쓰는 사이에 다른 요청이 끼어들지 못하게 한다(동시 요청).
 * 시각은 서버 시계(테스트에서 돌릴 수 있음)로 넘기고, Redis의 만료는 청소용으로만 쓴다.
 */
final class VerificationScripts {

	private VerificationScripts() {
	}

	/**
	 * 코드를 확인한다. 해시에 코드가 없으면 아무것도 쓰지 않는다(빈 기록을 새로 만들지 않는다).
	 *
	 * <p>KEYS[1] 해시. ARGV: 1 입력 코드, 2 지금(밀리초), 3 최대 틀린 횟수, 4 코드 필드, 5 만료 필드, 6 횟수 필드,
	 * 7 맞으면 지울지("1" / "0"), 8 · 9 맞았을 때 함께 읽을 필드(없으면 빈 값).
	 *
	 * <p>8 · 9의 필드는 코드를 비교한 바로 그 순간의 값이다. 코드를 다시 보내 기록이 바뀌어도 확인한 코드와 어긋나지 않는다.
	 *
	 * @return [결과({@code MATCH}, {@code MISMATCH}, {@code EXPIRED}), 8의 값, 9의 값]. 값이 없으면 빈 문자열
	 */
	@SuppressWarnings("rawtypes")
	static final RedisScript<List> VERIFY = new DefaultRedisScript<>("""
			local code = redis.call('HGET', KEYS[1], ARGV[4])
			local expiresAt = tonumber(redis.call('HGET', KEYS[1], ARGV[5]))
			if (not code) or (not expiresAt) then
			  return {'EXPIRED', '', ''}
			end
			if tonumber(ARGV[2]) >= expiresAt then
			  return {'EXPIRED', '', ''}
			end
			if code == ARGV[1] then
			  local first = ''
			  local second = ''
			  if ARGV[8] ~= '' then first = redis.call('HGET', KEYS[1], ARGV[8]) or '' end
			  if ARGV[9] ~= '' then second = redis.call('HGET', KEYS[1], ARGV[9]) or '' end
			  if ARGV[7] == '1' then
			    redis.call('HDEL', KEYS[1], ARGV[4], ARGV[5], ARGV[6])
			  end
			  return {'MATCH', first, second}
			end
			local attempts = redis.call('HINCRBY', KEYS[1], ARGV[6], 1)
			if attempts >= tonumber(ARGV[3]) then
			  redis.call('HDEL', KEYS[1], ARGV[4], ARGV[5], ARGV[6])
			end
			return {'MISMATCH', '', ''}
			""", List.class);

	/**
	 * 보낼 자리를 잡는다. 마지막으로 보낸 뒤 간격이 지났으면 지금 시각을 적고 1, 아니면 0.
	 * 해시 기록이어야 하는데 없으면(가입 기록이 사라짐) -1.
	 *
	 * <p>KEYS[1] 키. ARGV: 1 지금(밀리초), 2 간격(밀리초), 3 해시 필드(문자열 키면 빈 값),
	 * 4 해시가 있는지 볼 필드(문자열 키면 빈 값).
	 */
	static final RedisScript<Long> RESERVE_SEND = new DefaultRedisScript<>("""
			local now = tonumber(ARGV[1])
			local interval = tonumber(ARGV[2])
			local last
			if ARGV[3] == '' then
			  last = tonumber(redis.call('GET', KEYS[1]))
			else
			  if redis.call('HEXISTS', KEYS[1], ARGV[4]) == 0 then
			    return -1
			  end
			  last = tonumber(redis.call('HGET', KEYS[1], ARGV[3]))
			end
			if last and now < last + interval then
			  return 0
			end
			if ARGV[3] == '' then
			  redis.call('SET', KEYS[1], ARGV[1], 'PX', interval)
			else
			  redis.call('HSET', KEYS[1], ARGV[3], ARGV[1])
			end
			return 1
			""", Long.class);

	/** {@link #VERIFY}의 결과. */
	static VerificationCode.Result toResult(List<?> values) {
		return VerificationCode.Result.valueOf((String) values.get(0));
	}

	/** {@link #VERIFY}가 함께 읽은 값. 없으면 null. */
	static String field(List<?> values, int index) {
		String value = (String) values.get(index);
		return value == null || value.isEmpty() ? null : value;
	}

}
