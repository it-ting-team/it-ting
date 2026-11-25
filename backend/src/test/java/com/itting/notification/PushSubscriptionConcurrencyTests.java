package com.itting.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import com.itting.notification.dto.PushSubscriptionRequest;
import com.itting.notification.service.PushSubscriptionService;
import com.itting.notification.service.PushSubscriptionService.Registration;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 브라우저(같은 주소)의 등록이 동시에 들어와도 오류(500)가 나지 않고 줄이 하나이며, 새로 만든 요청은 하나뿐이다.
 * 두 트랜잭션이 실제로 겹쳐 커밋되어야 하므로 테스트 트랜잭션을 쓰지 않고, 만든 줄은 끝에 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PushSubscriptionConcurrencyTests {

	private static final int ROUNDS = 15;

	private static final int THREADS = 6;

	private static final String P256DH = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM";

	@Autowired
	PushSubscriptionService pushSubscriptionService;

	@Autowired
	TestUsers testUsers;

	@Autowired
	JdbcTemplate jdbcTemplate;

	long userA;

	long userB;

	@BeforeEach
	void setUp() {
		userA = testUsers.createEmailUser("push-race-a@example.com", "mole1234!");
		userB = testUsers.createEmailUser("push-race-b@example.com", "mole1234!");
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM push_subscription WHERE user_id IN (?, ?)", userA, userB);
		for (long userId : List.of(userA, userB)) {
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
	}

	@Test
	void 같은_계정이_같은_브라우저를_동시에_등록해도_오류가_없고_줄이_하나이며_새로_만든_요청은_하나다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			String endpoint = "https://push.example.com/race/same-user/" + round;

			List<Registration> results = race(thread -> pushSubscriptionService.register(userA, request(endpoint)));

			assertThat(results).hasSize(THREADS);
			assertThat(results).filteredOn(Registration::created).as("새 줄을 만든 요청").hasSize(1);
			assertThat(results).extracting(Registration::pushSubscriptionId).containsOnly(idOf(endpoint));
			assertThat(countOf(endpoint)).isEqualTo(1);
		}
	}

	@Test
	void 서로_다른_계정이_같은_브라우저를_동시에_등록해도_오류가_없고_줄이_하나이며_주인은_둘_가운데_하나다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			String endpoint = "https://push.example.com/race/two-users/" + round;

			List<Registration> results = race(thread -> pushSubscriptionService
					.register(thread % 2 == 0 ? userA : userB, request(endpoint)));

			assertThat(results).filteredOn(Registration::created).as("새 줄을 만든 요청").hasSize(1);
			assertThat(results).extracting(Registration::pushSubscriptionId).containsOnly(idOf(endpoint));
			assertThat(countOf(endpoint)).isEqualTo(1);
			assertThat(jdbcTemplate.queryForObject("SELECT user_id FROM push_subscription WHERE endpoint = ?",
					Long.class, endpoint)).isIn(userA, userB);
		}
	}

	private static PushSubscriptionRequest request(String endpoint) {
		return new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys(P256DH, "tBHItJI5svbpez7KI4CCXg"));
	}

	private long idOf(String endpoint) {
		return jdbcTemplate.queryForObject("SELECT id FROM push_subscription WHERE endpoint = ?", Long.class, endpoint);
	}

	private int countOf(String endpoint) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM push_subscription WHERE endpoint = ?", Integer.class,
				endpoint);
	}

	/** 같은 일을 여러 스레드가 동시에 시작해 모두 끝날 때까지 기다린다. 어느 스레드에서든 예외가 나면 테스트가 실패한다. */
	private static <T> List<T> race(IntFunction<T> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		try {
			CyclicBarrier start = new CyclicBarrier(THREADS);
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				int thread = i;
				Callable<T> call = () -> {
					start.await(10, TimeUnit.SECONDS);
					return task.apply(thread);
				};
				futures.add(pool.submit(call));
			}
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
