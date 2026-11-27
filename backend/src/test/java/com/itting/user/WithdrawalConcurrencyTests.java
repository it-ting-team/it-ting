package com.itting.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.itting.chat.dto.ChatRequestCreateRequest;
import com.itting.chat.service.ChatRequestService;
import com.itting.global.error.BusinessException;
import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.NotificationSteps;
import com.itting.support.TestcontainersConfiguration;
import com.itting.user.dto.WithdrawRequest;
import com.itting.user.service.WithdrawalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 탈퇴가 하나의 트랜잭션으로 묶이는지와, 대화 요청과 겹쳐도 탈퇴한 계정에 요청이 남지 않는지({@code docs/work/KAN-61.md}의 정한 것 C)를
 * 본다. 두 트랜잭션이 실제로 겹쳐 커밋되고 중간에 실패한 트랜잭션이 정말 되돌려져야 하므로 테스트 트랜잭션을 쓰지 않고, 만든 줄은
 * 끝에 지운다. 경합은 한 번으로는 우연히 지나갈 수 있어 여러 번 겨룬다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WithdrawalConcurrencyTests {

	private static final int ROUNDS = 10;

	private static final String PASSWORD = CommunitySteps.PASSWORD;

	@Autowired
	WithdrawalService withdrawalService;

	@Autowired
	ChatRequestService chatRequestService;

	@Autowired
	CommunitySteps community;

	@Autowired
	ChatSteps chat;

	@Autowired
	NotificationSteps notifications;

	@Autowired
	JdbcTemplate jdbcTemplate;

	/** 이 테스트가 만든 계정. 끝에 모두 지운다. */
	private final List<Long> created = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		if (created.isEmpty()) {
			return;
		}
		long[] ids = created.stream().mapToLong(Long::longValue).toArray();
		String in = created.stream().map(String::valueOf).collect(Collectors.joining(","));
		chat.deleteChatOf(ids);
		jdbcTemplate.update("DELETE FROM meetup_application WHERE user_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM meetup_like WHERE user_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM meetup_application WHERE meetup_id IN (SELECT id FROM meetup WHERE host_id IN ("
				+ in + "))");
		jdbcTemplate.update("DELETE FROM meetup WHERE host_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM user_withdrawal WHERE user_id IN (" + in + ")");
		chat.deleteAllOf(ids);
		created.clear();
	}

	@Test
	void 탈퇴와_그_계정에게_오는_대화_요청이_겹쳐도_탈퇴한_계정에_요청이_남지_않는다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			long leaver = member("withdraw-race-in-" + round + "-a@example.com", "경합" + round + "가");
			long sender = member("withdraw-race-in-" + round + "-b@example.com", "경합" + round + "나");

			List<Outcome> outcomes = race(() -> withdraw(leaver),
					() -> chatRequestService.send(sender, new ChatRequestCreateRequest(leaver, "안녕하세요")));

			// 탈퇴는 늘 성공한다. 보내기는 먼저 들어가 탈퇴에 지워지거나, 탈퇴한 계정이라 USER_NOT_FOUND로 거절된다.
			assertBusinessErrorsOnly(outcomes);
			assertThat(outcomes.getFirst().error()).as("%d번째 겨루기의 탈퇴", round).isNull();
			assertThat(status(leaver)).isEqualTo("WITHDRAWN");
			assertThat(chat.requestsBetween(leaver, sender)).as("%d번째 겨루기의 요청", round).isEmpty();
			assertThat(count("SELECT COUNT(*) FROM chat_request WHERE sender_id = ? OR receiver_id = ?", leaver, leaver))
					.isZero();
			// 받은 알림도 남지 않는다.
			assertThat(count("SELECT COUNT(*) FROM notification WHERE user_id = ?", leaver)).isZero();
		}
	}

	@Test
	void 탈퇴와_그_계정이_보내는_대화_요청이_겹쳐도_탈퇴한_계정이_보낸_요청이_남지_않는다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			long leaver = member("withdraw-race-out-" + round + "-a@example.com", "경합" + round + "다");
			long receiver = member("withdraw-race-out-" + round + "-b@example.com", "경합" + round + "라");

			List<Outcome> outcomes = race(() -> withdraw(leaver),
					() -> chatRequestService.send(leaver, new ChatRequestCreateRequest(receiver, "안녕하세요")));

			// 보내기는 먼저 들어가 탈퇴에 지워지거나, 탈퇴한 계정이라 UNAUTHENTICATED로 거절된다.
			assertBusinessErrorsOnly(outcomes);
			assertThat(outcomes.getFirst().error()).as("%d번째 겨루기의 탈퇴", round).isNull();
			assertThat(status(leaver)).isEqualTo("WITHDRAWN");
			assertThat(count("SELECT COUNT(*) FROM chat_request WHERE sender_id = ? OR receiver_id = ?", leaver, leaver))
					.as("%d번째 겨루기의 요청", round).isZero();
		}
	}

	@Test
	void 정리_도중_실패하면_모두_되돌려지고_원인을_고친_뒤_다시_하면_탈퇴된다() {
		long host = member("withdraw-rollback-host@example.com", "되돌림주최");
		long leaver = member("withdraw-rollback-a@example.com", "되돌림가");
		long friend = member("withdraw-rollback-b@example.com", "되돌림나");
		long stranger = member("withdraw-rollback-c@example.com", "되돌림다");
		Instant now = Instant.now();
		long soon = community.insertMeetup(host, "곧 시작하는 모임", now.plus(Duration.ofHours(2)), 2, null);
		long later = community.insertMeetup(host, "나중 모임", now.plus(Duration.ofDays(3)), 4, null);
		community.apply(soon, leaver, "CONFIRMED");
		community.apply(later, leaver, "PENDING");
		long room = chat.insertDirectRoom(leaver, friend, now.minus(Duration.ofHours(3)));
		chat.text(room, friend, "안녕", now.minus(Duration.ofHours(2)));
		chat.insertRequest(leaver, stranger, "보낸 요청", "PENDING", now.minus(Duration.ofHours(1)), null);
		notifications.insertPostCommented(leaver, friend, 1L, now.minus(Duration.ofHours(1)), null);
		// 마지막 단계(탈퇴 기록)에서 UNIQUE(user_id)에 걸려 실패하게 한다. 앞의 모든 정리가 이미 일어난 뒤다.
		jdbcTemplate.update("INSERT INTO user_withdrawal (user_id, reason, created_at) VALUES (?, NULL, UTC_TIMESTAMP(6))",
				leaver);

		assertThatThrownBy(() -> withdraw(leaver)).isInstanceOf(DataAccessException.class);

		assertThat(status(leaver)).isEqualTo("ACTIVE");
		assertThat(jdbcTemplate.queryForObject("SELECT email FROM `user` WHERE id = ?", String.class, leaver)).isNotNull();
		assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM `user` WHERE id = ?", String.class, leaver))
				.isNotNull();
		assertThat(count("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", leaver)).isEqualTo(1);
		assertThat(applicationStatus(soon, leaver)).isEqualTo("CONFIRMED");
		assertThat(applicationStatus(later, leaver)).isEqualTo("PENDING");
		assertThat(count("SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = 'MEETUP_PARTICIPATION_CANCELED'",
				host)).isZero();
		assertThat(chat.leftAt(room, leaver)).isNull();
		assertThat(chat.messages(room)).hasSize(1);
		assertThat(count("SELECT COUNT(*) FROM chat_request WHERE sender_id = ?", leaver)).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM notification WHERE user_id = ?", leaver)).isEqualTo(1);

		jdbcTemplate.update("DELETE FROM user_withdrawal WHERE user_id = ?", leaver);
		withdraw(leaver);

		assertThat(status(leaver)).isEqualTo("WITHDRAWN");
		assertThat(applicationStatus(soon, leaver)).isEqualTo("CANCELED");
		assertThat(applicationStatus(later, leaver)).isEqualTo("WITHDRAWN");
		assertThat(count("SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = 'MEETUP_PARTICIPATION_CANCELED'",
				host)).isEqualTo(1);
		assertThat(chat.leftAt(room, leaver)).isNotNull();
		assertThat(chat.messages(room)).hasSize(2);
		assertThat(count("SELECT COUNT(*) FROM chat_request WHERE sender_id = ?", leaver)).isZero();
		assertThat(count("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", leaver)).isZero();
		assertThat(count("SELECT COUNT(*) FROM notification WHERE user_id = ?", leaver)).isZero();
		assertThat(count("SELECT COUNT(*) FROM user_withdrawal WHERE user_id = ?", leaver)).isEqualTo(1);
	}

	// ---- 도구 ----

	private long member(String email, String nickname) {
		long userId = community.createMember(email, nickname);
		created.add(userId);
		return userId;
	}

	/** 세션이 없는 요청으로 비밀번호 계정이 탈퇴한다. */
	private Object withdraw(long userId) {
		withdrawalService.withdraw(userId, new WithdrawRequest(PASSWORD, null), new MockHttpServletRequest());
		return "withdrawn";
	}

	private String status(long userId) {
		return jdbcTemplate.queryForObject("SELECT status FROM `user` WHERE id = ?", String.class, userId);
	}

	private String applicationStatus(long meetupId, long userId) {
		return jdbcTemplate.queryForObject("SELECT status FROM meetup_application WHERE meetup_id = ? AND user_id = ?",
				String.class, meetupId, userId);
	}

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

	/** 한 일의 결과. 값이거나 던진 예외다. */
	private record Outcome(Object value, Throwable error) {
	}

	/** {@link BusinessException}이 아닌 예외(교착 상태 같은 것)는 테스트를 실패시킨다. */
	private static void assertBusinessErrorsOnly(List<Outcome> outcomes) {
		for (Outcome outcome : outcomes) {
			if (outcome.error() != null && !(outcome.error() instanceof BusinessException)) {
				throw new AssertionError("업무 오류가 아닌 예외가 났습니다.", outcome.error());
			}
		}
	}

	/** 두 일을 두 스레드가 동시에 시작해 둘 다 끝날 때까지 기다리고, 일마다 결과를 모은다(넘긴 순서대로). */
	private static List<Outcome> race(Callable<?>... tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
		try {
			CyclicBarrier start = new CyclicBarrier(tasks.length);
			List<Future<Outcome>> futures = new ArrayList<>();
			for (Callable<?> task : tasks) {
				futures.add(pool.submit(() -> {
					start.await(10, TimeUnit.SECONDS);
					try {
						return new Outcome(task.call(), null);
					}
					catch (Throwable error) {
						return new Outcome(null, error);
					}
				}));
			}
			List<Outcome> outcomes = new ArrayList<>();
			for (Future<Outcome> future : futures) {
				outcomes.add(future.get(60, TimeUnit.SECONDS));
			}
			return outcomes;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
