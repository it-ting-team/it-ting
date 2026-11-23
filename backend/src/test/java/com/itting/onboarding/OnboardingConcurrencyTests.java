package com.itting.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.itting.onboarding.dto.OnboardingCompleteRequest;
import com.itting.onboarding.service.OnboardingService;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 온보딩 확정을 동시에 할 때. 테스트 트랜잭션 없이 실제로 커밋하므로 끝나면 만든 행을 지운다.
 * {@code @AutoConfigureMockMvc}는 쓰지 않지만 다른 API 테스트와 같은 스프링 컨텍스트를 다시 쓰려고 붙인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OnboardingConcurrencyTests {

	private static final int USERS = 4;

	@Autowired
	OnboardingService onboardingService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Autowired
	OnboardingSteps steps;

	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (Long userId : userIds) {
			jdbcTemplate.update("""
					DELETE e FROM burrow_entrance e JOIN burrow b ON b.id = e.burrow_id WHERE b.user_id = ?
					""", userId);
			for (String table : List.of("burrow", "user_answer", "user_interest")) {
				jdbcTemplate.update("DELETE FROM " + table + " WHERE user_id = ?", userId);
			}
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
		steps.clearNicknameHolds();
	}

	@Test
	void 같은_층에서_동시에_확정해도_자리_번호가_겹치지_않는다() throws Exception {
		for (int i = 0; i < USERS; i++) {
			userIds.add(testUsers.createEmailUser("concurrent" + i + "@example.com", "mole1234!"));
		}
		List<Long> interests = steps.interestIds(3);
		List<OnboardingCompleteRequest.Answer> answers = steps.questionIds(3).stream()
				.map(id -> new OnboardingCompleteRequest.Answer(id, "동시에 확정하는 사람의 답변"))
				.toList();
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(USERS);
		try {
			List<Future<OnboardingService.Result>> results = new ArrayList<>();
			for (int i = 0; i < USERS; i++) {
				Long userId = userIds.get(i);
				OnboardingCompleteRequest request = new OnboardingCompleteRequest("동시" + i,
						new OnboardingCompleteRequest.Mole(null, null, null, null), interests, answers);
				results.add(executor.submit(() -> {
					start.await();
					return onboardingService.complete(userId, request);
				}));
			}
			start.countDown();
			for (Future<OnboardingService.Result> result : results) {
				assertThat(result.get(30, TimeUnit.SECONDS).created()).isTrue();
			}
		}
		finally {
			executor.shutdownNow();
		}

		List<Integer> slots = jdbcTemplate.queryForList("""
				SELECT e.slot FROM burrow_entrance e JOIN burrow b ON b.id = e.burrow_id
				WHERE b.user_id IN (%s) ORDER BY e.slot
				""".formatted(String.join(",", userIds.stream().map(String::valueOf).toList())), Integer.class);
		assertThat(slots).doesNotHaveDuplicates().hasSize(USERS);
	}

	@Test
	void 같은_계정이_동시에_확정해도_땅굴은_하나다() throws Exception {
		long userId = testUsers.createEmailUser("twice@example.com", "mole1234!");
		userIds.add(userId);
		OnboardingCompleteRequest request = new OnboardingCompleteRequest("두번누름",
				new OnboardingCompleteRequest.Mole(null, null, null, null), steps.interestIds(3),
				steps.questionIds(3).stream()
						.map(id -> new OnboardingCompleteRequest.Answer(id, "두 번 누른 사람의 답변입니다"))
						.toList());
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<OnboardingService.Result> first = executor.submit(() -> {
				start.await();
				return onboardingService.complete(userId, request);
			});
			Future<OnboardingService.Result> second = executor.submit(() -> {
				start.await();
				return onboardingService.complete(userId, request);
			});
			start.countDown();
			OnboardingService.Result a = first.get(30, TimeUnit.SECONDS);
			OnboardingService.Result b = second.get(30, TimeUnit.SECONDS);

			assertThat(List.of(a.created(), b.created())).containsExactlyInAnyOrder(true, false);
			assertThat(a.response()).isEqualTo(b.response());
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM burrow WHERE user_id = ?", Integer.class, userId))
				.isEqualTo(1);
	}

}
