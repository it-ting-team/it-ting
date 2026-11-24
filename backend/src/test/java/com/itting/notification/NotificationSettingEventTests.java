package com.itting.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.itting.notification.dto.NotificationSettingUpdateRequest;
import com.itting.notification.service.NotificationSettingService;
import com.itting.notification.service.NotificationUnreadChangedEvent;
import com.itting.support.NotificationSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * 알림 설정을 바꿔 빨간 점이 달라질 때만 실시간 소식을 낸다. 이벤트는 커밋 뒤에 나가므로 테스트 트랜잭션을 쓰지 않고,
 * 만든 줄은 끝에 지운다. 같은 계정의 처음 저장이 겹칠 때도 여기서 본다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class NotificationSettingEventTests {

	@Autowired
	NotificationSettingService notificationSettingService;

	@Autowired
	TestUsers testUsers;

	@Autowired
	NotificationSteps steps;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ApplicationEvents events;

	long receiver;

	long actor;

	Instant now;

	@BeforeEach
	void setUp() {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		receiver = testUsers.createEmailUser("setting-receiver@example.com", "mole1234!");
		actor = testUsers.createEmailUser("setting-actor@example.com", "mole1234!");
	}

	@AfterEach
	void cleanUp() {
		for (long userId : List.of(receiver, actor)) {
			jdbcTemplate.update("DELETE FROM notification WHERE user_id = ? OR actor_id = ?", userId, userId);
			jdbcTemplate.update("DELETE FROM notification_setting WHERE user_id = ?", userId);
		}
		for (long userId : List.of(receiver, actor)) {
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
	}

	@Test
	void 안_읽은_알림이_있는_갈래를_끄면_빨간_점이_꺼지므로_실시간_소식을_낸다() {
		steps.insertPostCommented(receiver, actor, 10L, now.minus(Duration.ofMinutes(1)), null);

		notificationSettingService.updateSettings(receiver, request(true, true, false, true, true));

		assertThat(events.stream(NotificationUnreadChangedEvent.class))
				.containsExactly(new NotificationUnreadChangedEvent(receiver));
	}

	@Test
	void 꺼둔_갈래를_다시_켜면_빨간_점이_켜지므로_실시간_소식을_낸다() {
		steps.insertPostCommented(receiver, actor, 10L, now.minus(Duration.ofMinutes(1)), null);
		notificationSettingService.updateSettings(receiver, request(true, true, false, true, true));
		events.clear();

		notificationSettingService.updateSettings(receiver, request(true, true, true, true, true));

		assertThat(events.stream(NotificationUnreadChangedEvent.class))
				.containsExactly(new NotificationUnreadChangedEvent(receiver));
	}

	@Test
	void 빨간_점이_달라지지_않으면_실시간_소식을_내지_않는다() {
		steps.insertPostCommented(receiver, actor, 10L, now.minus(Duration.ofMinutes(1)), null);

		// 안 읽은 알림이 없는 갈래를 끈다. 커뮤니티의 안 읽은 알림이 남아 있어 빨간 점은 그대로 켜져 있다.
		notificationSettingService.updateSettings(receiver, request(false, true, true, true, true));
		// 같은 값을 다시 보낸다.
		notificationSettingService.updateSettings(receiver, request(false, true, true, true, true));

		assertThat(events.stream(NotificationUnreadChangedEvent.class)).isEmpty();
	}

	@Test
	void 안_읽은_알림이_없으면_갈래를_끄고_켜도_실시간_소식을_내지_않는다() {
		notificationSettingService.updateSettings(receiver, request(true, true, false, true, true));
		notificationSettingService.updateSettings(receiver, request(true, true, true, true, true));

		assertThat(events.stream(NotificationUnreadChangedEvent.class)).isEmpty();
	}

	@Test
	void 같은_계정의_처음_저장이_겹쳐도_줄은_하나이고_모두_성공한다() throws Exception {
		int requests = 4;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(requests);
		try {
			List<Future<?>> results = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				boolean on = i % 2 == 0;
				results.add(executor.submit(() -> {
					start.await();
					return notificationSettingService.updateSettings(receiver, request(on, on, on, on, on));
				}));
			}
			start.countDown();
			for (Future<?> result : results) {
				result.get(30, TimeUnit.SECONDS);
			}
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_setting WHERE user_id = ?",
				Integer.class, receiver)).isEqualTo(1);
	}

	private static NotificationSettingUpdateRequest request(boolean meetup, boolean chat, boolean community,
			boolean burrow, boolean notice) {
		return new NotificationSettingUpdateRequest(meetup, chat, community, burrow, notice);
	}

}
