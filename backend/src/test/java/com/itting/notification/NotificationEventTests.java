package com.itting.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationCreatedEvent;
import com.itting.notification.service.NotificationService;
import com.itting.notification.service.NotificationUnreadChangedEvent;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 이벤트는 알림을 만든 트랜잭션이 커밋된 뒤에만 나간다. 커밋해야 하므로 테스트 트랜잭션을 쓰지 않고, 만든 줄은 끝에 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class NotificationEventTests {

	@Autowired
	NotificationService notificationService;

	@Autowired
	TestUsers testUsers;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	ApplicationEvents events;

	long receiver;

	long actor;

	@BeforeEach
	void setUp() {
		receiver = testUsers.createEmailUser("event-receiver@example.com", "mole1234!");
		actor = testUsers.createEmailUser("event-actor@example.com", "mole1234!");
	}

	@AfterEach
	void cleanUp() {
		for (long userId : List.of(receiver, actor)) {
			jdbcTemplate.update("DELETE FROM notification WHERE user_id = ? OR actor_id = ?", userId, userId);
		}
		for (long userId : List.of(receiver, actor)) {
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
	}

	@Test
	void 커밋하면_생긴_것과_안_읽은_수가_바뀐_것을_낸다() {
		transactionTemplate.executeWithoutResult(status -> {
			notificationService.notify(receiver, NotificationType.POST_COMMENTED, actor, 1L);
			assertThat(events.stream(NotificationCreatedEvent.class)).isEmpty();
		});

		Long notificationId = jdbcTemplate.queryForObject("SELECT id FROM notification WHERE user_id = ?",
				Long.class, receiver);
		assertThat(events.stream(NotificationCreatedEvent.class))
				.containsExactly(new NotificationCreatedEvent(notificationId, receiver));
		assertThat(events.stream(NotificationUnreadChangedEvent.class))
				.containsExactly(new NotificationUnreadChangedEvent(receiver));
	}

	@Test
	void 되돌리면_알림도_이벤트도_없다() {
		transactionTemplate.executeWithoutResult(status -> {
			notificationService.notify(receiver, NotificationType.POST_COMMENTED, actor, 1L);
			notificationService.notifyChatMessage(receiver, 9L, actor);
			status.setRollbackOnly();
		});

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ?", Integer.class,
				receiver)).isZero();
		assertThat(events.stream(NotificationCreatedEvent.class)).isEmpty();
		assertThat(events.stream(NotificationUnreadChangedEvent.class)).isEmpty();
	}

	@Test
	void 대화방을_읽어_바뀐_것이_있을_때만_안_읽은_수_이벤트를_낸다() {
		transactionTemplate.executeWithoutResult(status -> notificationService.notifyChatMessage(receiver, 9L, actor));
		events.clear();

		transactionTemplate.executeWithoutResult(status -> notificationService.readChatRoom(receiver, 9L));
		transactionTemplate.executeWithoutResult(status -> notificationService.readChatRoom(receiver, 9L));

		assertThat(events.stream(NotificationUnreadChangedEvent.class)).hasSize(1);
	}

}
