package com.itting.support;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 온보딩 테스트가 함께 쓰는 값. 관심사와 질문은 마이그레이션의 처음 값을 쓴다.
 */
@Component
public class OnboardingSteps {

	public static final String HOLD_KEY = "itting:nickname-hold:";

	public static final String HOLD_USER_KEY = "itting:nickname-hold-user:";

	private final JdbcTemplate jdbcTemplate;

	private final StringRedisTemplate redis;

	public OnboardingSteps(JdbcTemplate jdbcTemplate, StringRedisTemplate redis) {
		this.jdbcTemplate = jdbcTemplate;
		this.redis = redis;
	}

	/** 지우지 않은 세부 관심사 가운데 id가 작은 것부터 {@code count}개. */
	public List<Long> interestIds(int count) {
		return jdbcTemplate.queryForList(
				"SELECT id FROM interest WHERE deleted_at IS NULL ORDER BY id LIMIT ?", Long.class, count);
	}

	/** 자기소개 질문 가운데 id가 작은 것부터 {@code count}개. */
	public List<Long> questionIds(int count) {
		return jdbcTemplate.queryForList("SELECT id FROM intro_question ORDER BY id LIMIT ?", Long.class, count);
	}

	/** 확정 API의 본문. 두더지는 머리 · 목만 고르고, 답변은 질문마다 정해 둔 글이다. */
	public String completeBody(String nickname, List<Long> interestIds, List<Long> questionIds) {
		String interests = interestIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
		String answers = questionIds.stream()
				.map(id -> "{ \"introQuestionId\": %d, \"answer\": \"질문 %d에 대한 열 글자 넘는 답\" }".formatted(id, id))
				.collect(Collectors.joining(", "));
		return """
				{
				  "nickname": "%s",
				  "mole": { "head": "CAP_RED", "face": null, "neck": "SCARF_GREEN", "hand": null },
				  "interestIds": [%s],
				  "answers": [%s]
				}
				""".formatted(nickname, interests, answers);
	}

	/** 기본 관심사 3개 · 질문 3개로 만든 본문. */
	public String completeBody(String nickname) {
		return completeBody(nickname, interestIds(3), questionIds(3));
	}

	/** 맡아 둔 닉네임을 모두 지운다. Redis는 테스트 트랜잭션과 함께 되돌려지지 않는다. */
	public void clearNicknameHolds() {
		Set<String> keys = redis.keys("itting:nickname-hold*");
		if (keys != null && !keys.isEmpty()) {
			redis.delete(keys);
		}
	}

	/** 이 닉네임(소문자)을 맡은 계정의 id. 맡은 계정이 없으면 null. */
	public String holder(String nickname) {
		return redis.opsForValue().get(HOLD_KEY + nickname.toLowerCase());
	}

	/** 지금 열린 학기보다 늦게 열린 학기를 더해, 이미 온보딩을 마친 계정이 새 학기를 고르지 않은 상태가 되게 한다. */
	public long openNewTerm() {
		jdbcTemplate.update("""
				INSERT INTO term (year, half, opened_at, created_at, updated_at)
				VALUES (2027, 1, UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""");
		return jdbcTemplate.queryForObject("SELECT id FROM term WHERE year = 2027 AND half = 1", Long.class);
	}

	public long currentTermId() {
		return jdbcTemplate.queryForObject("""
				SELECT id FROM term WHERE opened_at <= UTC_TIMESTAMP(6) ORDER BY opened_at DESC, id DESC LIMIT 1
				""", Long.class);
	}

}
