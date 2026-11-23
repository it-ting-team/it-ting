package com.itting.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationCleanupJob;
import com.itting.notification.service.NotificationService;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.NotificationSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 영역이 부를 알림 만들기 메서드와 30일 정리 작업.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class NotificationServiceTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	NotificationService notificationService;

	@Autowired
	NotificationCleanupJob cleanupJob;

	@Autowired
	NotificationSteps steps;

	@Autowired
	TestUsers testUsers;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MutableClock clock;

	@PersistenceContext
	EntityManager entityManager;

	long receiver;

	long actor;

	@BeforeEach
	void setUp() {
		clock.setNow(Instant.now().truncatedTo(ChronoUnit.MICROS));
		receiver = testUsers.createEmailUser("receiver@example.com", PASSWORD);
		actor = testUsers.createEmailUser("actor@example.com", PASSWORD);
	}

	@Test
	void notify는_갈_곳의_종류를_정해_한_줄을_만든다() {
		notificationService.notify(receiver, NotificationType.POST_COMMENTED, actor, 204L);

		List<Map<String, Object>> rows = rows(receiver);
		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst()).containsEntry("type", "POST_COMMENTED")
				.containsEntry("target_type", "POST")
				.containsEntry("target_id", 204L)
				.containsEntry("actor_id", actor)
				.containsEntry("read_at", null)
				.containsEntry("event_key", null);
	}

	@Test
	void 자기_자신_탈퇴한_사람_갈래를_끈_사람에게는_만들지_않는다() {
		long withdrawn = testUsers.createEmailUser("gone@example.com", PASSWORD);
		testUsers.withdraw(withdrawn);
		long communityOff = testUsers.createEmailUser("off@example.com", PASSWORD);
		steps.setting(communityOff, true, true, false, true, true);
		long meetupOff = testUsers.createEmailUser("meetup-off@example.com", PASSWORD);
		steps.setting(meetupOff, false, true, true, true, true);

		notificationService.notify(actor, NotificationType.POST_LIKED, actor, 1L);
		notificationService.notify(withdrawn, NotificationType.POST_LIKED, actor, 1L);
		notificationService.notify(communityOff, NotificationType.POST_LIKED, actor, 1L);
		notificationService.notify(meetupOff, NotificationType.POST_LIKED, actor, 1L);

		assertThat(steps.count(actor)).isZero();
		assertThat(steps.count(withdrawn)).isZero();
		assertThat(steps.count(communityOff)).isZero();
		// 다른 갈래를 끈 사람은 받는다.
		assertThat(rows(meetupOff)).hasSize(1);
	}

	@Test
	void notifyAll은_같은_사람에게_한_번만_만들고_일으킨_사람은_뺀다() {
		long third = testUsers.createEmailUser("third@example.com", PASSWORD);

		notificationService.notifyAll(List.of(receiver, third, receiver, actor), NotificationType.MEETUP_UPDATED,
				actor, 23L);

		assertThat(rows(receiver)).hasSize(1);
		assertThat(rows(third)).hasSize(1);
		assertThat(steps.count(actor)).isZero();
	}

	@Test
	void 일으킨_사람을_드러내지_않는_종류는_actor를_저장하지_않는다() {
		notificationService.notify(receiver, NotificationType.POST_HIDDEN, actor, 204L);
		notificationService.notify(receiver, NotificationType.MEETUP_KICKED, actor, 23L);

		assertThat(rows(receiver)).extracting(row -> row.get("actor_id")).containsOnlyNulls();
	}

	@Test
	void notifyOnce는_같은_키로_두_번_불러도_한_줄이다() {
		String key = "MEETUP_START_REMINDER:23:1795600800:24H";

		notificationService.notifyOnce(receiver, NotificationType.MEETUP_START_REMINDER, null, 23L, key);
		notificationService.notifyOnce(receiver, NotificationType.MEETUP_START_REMINDER, null, 23L, key);
		notificationService.notifyOnce(receiver, NotificationType.MEETUP_START_REMINDER, null, 23L,
				"MEETUP_START_REMINDER:23:1795600800:1H");

		assertThat(rows(receiver)).extracting(row -> row.get("event_key"))
				.containsExactlyInAnyOrder(key, "MEETUP_START_REMINDER:23:1795600800:1H");
	}

	@Test
	void notifyOnce는_빈_키를_받지_않는다() {
		assertThatThrownBy(
				() -> notificationService.notifyOnce(receiver, NotificationType.MEETUP_START_REMINDER, null, 23L, " "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 새_메시지_알림은_안_읽은_동안_대화방마다_한_줄이고_시각과_보낸_사람만_바뀐다() {
		long third = testUsers.createEmailUser("third@example.com", PASSWORD);
		Instant first = clock.instant();
		notificationService.notifyChatMessage(receiver, 9L, actor);
		clock.advance(Duration.ofMinutes(3));
		notificationService.notifyChatMessage(receiver, 9L, third);
		notificationService.notifyChatMessage(receiver, 10L, actor);

		List<Map<String, Object>> room9 = rows(receiver).stream().filter(row -> row.get("target_id").equals(9L))
				.toList();
		assertThat(room9).hasSize(1);
		assertThat(room9.getFirst()).containsEntry("actor_id", third).containsEntry("target_type", "CHAT_ROOM");
		assertThat(createdAt(room9.getFirst())).isEqualTo(first.plus(Duration.ofMinutes(3)));
		assertThat(rows(receiver)).hasSize(2);
	}

	@Test
	void 대화방을_읽으면_새_메시지_알림이_읽음이_되고_다음_메시지는_새_줄이다() {
		notificationService.notifyChatMessage(receiver, 9L, actor);
		notificationService.notifyChatMessage(receiver, 10L, actor);

		notificationService.readChatRoom(receiver, 9L);
		notificationService.notifyChatMessage(receiver, 9L, actor);

		List<Map<String, Object>> room9 = rows(receiver).stream().filter(row -> row.get("target_id").equals(9L))
				.toList();
		assertThat(room9).hasSize(2);
		assertThat(room9).filteredOn(row -> row.get("read_at") != null).hasSize(1)
				.allSatisfy(row -> assertThat(row.get("event_key")).isNull());
		assertThat(room9).filteredOn(row -> row.get("read_at") == null).hasSize(1);
		assertThat(rows(receiver)).filteredOn(row -> row.get("target_id").equals(10L))
				.allSatisfy(row -> assertThat(row.get("read_at")).isNull());
	}

	@Test
	void 정리_작업은_30일이_지난_알림만_지운다() {
		long old = steps.insertPostCommented(receiver, actor, 1L, clock.instant().minus(Duration.ofDays(31)), null);
		long recent = steps.insertPostCommented(receiver, actor, 2L, clock.instant().minus(Duration.ofDays(29)),
				null);

		assertThat(cleanupJob.deleteExpired()).isEqualTo(1);

		assertThat(rows(receiver)).extracting(row -> row.get("id")).containsExactly(recent);
		assertThat(old).isNotEqualTo(recent);
	}

	private List<Map<String, Object>> rows(long userId) {
		entityManager.flush();
		return jdbcTemplate.queryForList("""
				SELECT id, type, target_type, target_id, actor_id, read_at, created_at, event_key
				FROM notification WHERE user_id = ? ORDER BY id
				""", userId);
	}

	private static Instant createdAt(Map<String, Object> row) {
		Object value = row.get("created_at");
		if (value instanceof java.time.LocalDateTime local) {
			return local.toInstant(java.time.ZoneOffset.UTC);
		}
		return ((java.sql.Timestamp) value).toLocalDateTime().toInstant(java.time.ZoneOffset.UTC);
	}

}
