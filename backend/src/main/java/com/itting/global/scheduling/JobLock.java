package com.itting.global.scheduling;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 서버가 여러 대일 때 같은 예약 작업이 겹쳐 돌지 않게 Redis에 잠금을 건다(KAN-66 B). 키는
 * {@code itting:job-lock:<작업 이름>}이고, 잡은 서버만 작업을 돌린다. 끝나면 자기가 잡은 잠금만 푼다.
 *
 * <p>작업이 {@code ttl}보다 오래 걸리면 잠금이 풀려 다른 서버가 같은 작업을 시작할 수 있다. 그래서 이 잠금을 쓰는 작업도
 * 겹쳐 돌았을 때 깨지지 않게 만든다.
 */
@Component
public class JobLock {

	static final String KEY = "itting:job-lock:";

	/** 값이 내 표시일 때만 지운다. 내 잠금이 이미 풀리고 다른 서버가 잡았으면 그 잠금은 건드리지 않는다. */
	private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
			if redis.call('get', KEYS[1]) == ARGV[1] then
			  return redis.call('del', KEYS[1])
			end
			return 0
			""", Long.class);

	private final StringRedisTemplate redis;

	public JobLock(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/**
	 * 잠금을 잡으면 {@code job}을 돌리고 잠금을 푼다. 다른 서버가 잡고 있으면 돌리지 않는다.
	 *
	 * @param name 작업 이름. 키의 끝에 붙는다
	 * @param ttl 잠금이 저절로 풀리는 시간. 작업이 보통 걸리는 시간보다 넉넉히 준다
	 * @return 돌렸으면 true
	 */
	public boolean runExclusively(String name, Duration ttl, Runnable job) {
		String key = KEY + name;
		String token = UUID.randomUUID().toString();
		Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
		if (!Boolean.TRUE.equals(acquired)) {
			return false;
		}
		try {
			job.run();
			return true;
		}
		finally {
			redis.execute(RELEASE, List.of(key), token);
		}
	}

}
