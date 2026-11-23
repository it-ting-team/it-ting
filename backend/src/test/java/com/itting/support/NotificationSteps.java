package com.itting.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

/**
 * 알림 테스트가 함께 쓰는 값. 알림을 만드는 다른 영역의 API가 아직 없으므로 알림과 설정을 DB에 바로 넣는다.
 */
@Component
public class NotificationSteps {

	private final JdbcTemplate jdbcTemplate;

	private final TestUsers testUsers;

	private final OnboardingSteps onboardingSteps;

	@PersistenceContext
	private EntityManager entityManager;

	public NotificationSteps(JdbcTemplate jdbcTemplate, TestUsers testUsers, OnboardingSteps onboardingSteps) {
		this.jdbcTemplate = jdbcTemplate;
		this.testUsers = testUsers;
		this.onboardingSteps = onboardingSteps;
	}

	/**
	 * 온보딩을 마치고 이번 학기 서울 2층에 땅굴이 있는 계정. 알림 API를 부를 수 있다.
	 */
	public long createMember(String email, String password, String nickname) {
		long userId = testUsers.createEmailUser(email, password);
		jdbcTemplate.update("""
				UPDATE user_profile SET nickname = ?, mole_face = 'GLASSES_ROUND' WHERE user_id = ?
				""", nickname, userId);
		testUsers.markOnboarded(userId);
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement(
					"INSERT INTO burrow (user_id, created_at) VALUES (?, UTC_TIMESTAMP(6))", new String[] { "id" });
			statement.setLong(1, userId);
			return statement;
		}, keyHolder);
		long burrowId = keyHolder.getKey().longValue();
		long termId = onboardingSteps.currentTermId();
		long floorId = testUsers.seoulFloorId(2);
		jdbcTemplate.update("""
				INSERT INTO burrow_entrance (burrow_id, term_id, campus_floor_id, slot, created_at, updated_at)
				SELECT ?, ?, ?, COALESCE(MAX(slot), 0) + 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
				FROM burrow_entrance WHERE term_id = ? AND campus_floor_id = ?
				""", burrowId, termId, floorId, termId, floorId);
		entityManager.clear();
		return userId;
	}

	/**
	 * 알림을 넣는다.
	 *
	 * @param readAt 읽은 시각. 안 읽었으면 null
	 * @return 알림의 id
	 */
	public long insert(long userId, String type, String targetType, Long targetId, Long actorId, Instant createdAt,
			Instant readAt) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO notification (user_id, type, target_type, target_id, actor_id, read_at, created_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", new String[] { "id" });
			statement.setLong(1, userId);
			statement.setString(2, type);
			statement.setString(3, targetType);
			statement.setObject(4, targetId);
			statement.setObject(5, actorId);
			statement.setObject(6, readAt == null ? null : utc(readAt));
			statement.setObject(7, utc(createdAt));
			return statement;
		}, keyHolder);
		return keyHolder.getKey().longValue();
	}

	/** 글에 댓글이 달린 알림(갈래 COMMUNITY, 갈 곳 POST). */
	public long insertPostCommented(long userId, long actorId, long postId, Instant createdAt, Instant readAt) {
		return insert(userId, "POST_COMMENTED", "POST", postId, actorId, createdAt, readAt);
	}

	/** 갈래별 알림 설정. 넘긴 값대로 켜고 끈다. */
	public void setting(long userId, boolean meetup, boolean chat, boolean community, boolean burrow,
			boolean notice) {
		jdbcTemplate.update("""
				INSERT INTO notification_setting (user_id, meetup_on, chat_on, community_on, burrow_on, notice_on,
				                                  created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", userId, meetup, chat, community, burrow, notice);
		entityManager.clear();
	}

	public Instant readAt(long notificationId) {
		LocalDateTime value = jdbcTemplate.queryForObject("SELECT read_at FROM notification WHERE id = ?",
				LocalDateTime.class, notificationId);
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

	public int count(long userId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ?", Integer.class,
				userId);
	}

	/** JDBC 드라이버가 JVM 시간대로 바꾸지 않게 UTC의 날짜 · 시각 그대로 넘긴다. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
