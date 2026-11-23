package com.itting.user.repository;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * 닉네임을 확인한 계정 몫으로 잠깐 맡아 둔다({@code docs/api/03-onboarding.md}의 "닉네임 맡아 두기").
 *
 * <ul>
 * <li>{@code itting:nickname-hold:<닉네임>}: 맡은 계정의 id</li>
 * <li>{@code itting:nickname-hold-user:<계정 id>}: 그 계정이 맡은 닉네임. 한 계정은 하나만 맡는다</li>
 * </ul>
 *
 * <p>닉네임은 영어 소문자로 바꿔 키로 쓴다. DB의 UNIQUE가 대소문자를 가리지 않으므로 맡아 두기도 같게 맞춘다.
 * 두 키 모두 맡아 두는 시간이 지나면 Redis가 지운다. 확인 · 풀기 · 맡기는 Lua 스크립트로 한 번에 처리해
 * 두 사람이 같은 닉네임을 동시에 확인해도 한 사람만 맡는다.
 */
@Repository
public class NicknameHoldRepository {

	private static final String HOLD_KEY = "itting:nickname-hold:";

	private static final String USER_KEY = "itting:nickname-hold-user:";

	/**
	 * KEYS[1] 닉네임 키, KEYS[2] 계정 키. ARGV: 1 계정 id, 2 닉네임(소문자), 3 맡아 두는 시간(밀리초), 4 닉네임 키의 앞부분.
	 *
	 * @return 다른 계정이 맡아 두었으면 0, 맡았으면 1
	 */
	private static final RedisScript<Long> HOLD = new DefaultRedisScript<>("""
			local holder = redis.call('GET', KEYS[1])
			if holder and holder ~= ARGV[1] then
			  return 0
			end
			local previous = redis.call('GET', KEYS[2])
			if previous and previous ~= ARGV[2] then
			  local previousKey = ARGV[4] .. previous
			  if redis.call('GET', previousKey) == ARGV[1] then
			    redis.call('DEL', previousKey)
			  end
			end
			redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
			redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
			return 1
			""", Long.class);

	/** KEYS[1] 계정 키. ARGV: 1 계정 id, 2 닉네임 키의 앞부분. 이 계정이 맡은 닉네임을 푼다. */
	private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
			local nickname = redis.call('GET', KEYS[1])
			if nickname then
			  local holdKey = ARGV[2] .. nickname
			  if redis.call('GET', holdKey) == ARGV[1] then
			    redis.call('DEL', holdKey)
			  end
			  redis.call('DEL', KEYS[1])
			end
			return 1
			""", Long.class);

	private final StringRedisTemplate redis;

	public NicknameHoldRepository(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/**
	 * 닉네임을 이 계정 몫으로 맡는다. 이 계정이 맡아 둔 다른 닉네임은 푼다. 이미 이 계정이 맡은 닉네임이면 시간을 새로 센다.
	 *
	 * @return 다른 계정이 맡아 두어 맡지 못했으면 false
	 */
	public boolean hold(String nickname, Long userId, Duration ttl) {
		String key = normalize(nickname);
		Long result = redis.execute(HOLD, List.of(HOLD_KEY + key, USER_KEY + userId),
				String.valueOf(userId), key, String.valueOf(ttl.toMillis()), HOLD_KEY);
		return result != null && result == 1L;
	}

	/** 다른 계정이 이 닉네임을 맡아 두었는지. 맡아 둔 시간이 지났으면 false다. */
	public boolean isHeldByOther(String nickname, Long userId) {
		String holder = redis.opsForValue().get(HOLD_KEY + normalize(nickname));
		return holder != null && !holder.equals(String.valueOf(userId));
	}

	/** 이 계정이 맡아 둔 닉네임을 푼다. 닉네임을 저장했을 때(온보딩 확정, 내 정보 수정) 부른다. */
	public void release(Long userId) {
		redis.execute(RELEASE, List.of(USER_KEY + userId), String.valueOf(userId), HOLD_KEY);
	}

	private static String normalize(String nickname) {
		return nickname.toLowerCase(Locale.ROOT);
	}

}
