package com.itting.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.itting.user.dto.MoleUpdateRequest;
import com.itting.user.dto.ProfileUpdateRequest;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.MyProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 사람의 프로필 저장이 겹칠 때. 테스트 트랜잭션 없이 실제로 커밋하므로 끝나면 만든 행을 지운다.
 * {@code @AutoConfigureMockMvc}는 쓰지 않지만 다른 API 테스트와 같은 스프링 컨텍스트를 다시 쓰려고 붙인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MyProfileConcurrencyTests {

	private static final int MOVERS = 4;

	@Autowired
	MyProfileService myProfileService;

	@Autowired
	OnboardingService onboardingService;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Autowired
	OnboardingSteps steps;

	@Autowired
	PlatformTransactionManager transactionManager;

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

	/**
	 * 내 정보 저장이 프로필 행을 잠그고 자기소개를 바꾸는 동안 두더지를 저장한다. 두더지 저장은 잠금이 풀린 뒤의 프로필을 읽어야
	 * 한다. 잠그지 않고 먼저 읽으면 자기소개를 읽기 전의 값(null)으로 되돌린다.
	 */
	@Test
	void 내_정보를_저장하는_동안_두더지를_저장해도_내_정보가_되돌아가지_않는다() throws Exception {
		long userId = onboard("concurrent-mole@example.com", "동시두더지");
		MoleUpdateRequest mole = new MoleUpdateRequest(Optional.of("HAT_BLUE"), Optional.empty(), Optional.empty(),
				Optional.empty());

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<UserDisplayResponse.Mole> moleSaved = new TransactionTemplate(transactionManager).execute(status -> {
				jdbcTemplate.queryForObject("SELECT user_id FROM user_profile WHERE user_id = ? FOR UPDATE", Long.class,
						userId);
				Future<UserDisplayResponse.Mole> future = executor.submit(() -> myProfileService.changeMole(userId, mole));
				sleep(500);
				jdbcTemplate.update("UPDATE user_profile SET intro = ? WHERE user_id = ?", "먼저 저장한 자기소개", userId);
				return future;
			});
			assertThat(moleSaved.get(30, TimeUnit.SECONDS).head()).isEqualTo("HAT_BLUE");
		}
		finally {
			executor.shutdownNow();
		}

		Map<String, Object> profile = jdbcTemplate.queryForMap(
				"SELECT intro, mole_head FROM user_profile WHERE user_id = ?", userId);
		assertThat(profile).containsEntry("intro", "먼저 저장한 자기소개").containsEntry("mole_head", "HAT_BLUE");
	}

	/**
	 * 서로 다른 사람이 같은 층으로 동시에 옮긴다. 프로필의 캠퍼스-층을 먼저 UPDATE하면 외래 키 검사가 목적지 {@code campus_floor}
	 * 행에 공유 잠금을 걸고, 그 뒤 자리 번호를 정하려고 배타 잠금을 요청하면 서로 기다려 교착이 생긴다. 모두 저장되어야 한다.
	 */
	@Test
	void 여러_사람이_같은_층으로_동시에_옮겨도_모두_저장되고_자리_번호가_겹치지_않는다() throws Exception {
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < MOVERS; i++) {
			ids.add(onboard("mover" + i + "@example.com", "이사" + i));
		}
		long floor5 = testUsers.seoulFloorId(5);
		List<ProfileUpdateRequest.Answer> answers = steps.questionIds(3).stream()
				.map(id -> new ProfileUpdateRequest.Answer(id, "동시에 이사하는 사람의 답변"))
				.toList();
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(MOVERS);
		try {
			List<Future<?>> results = new ArrayList<>();
			for (int i = 0; i < MOVERS; i++) {
				Long id = ids.get(i);
				ProfileUpdateRequest request = new ProfileUpdateRequest("이사" + i, floor5, steps.interestIds(3), answers,
						Optional.of("같은 층으로 옮깁니다"));
				results.add(executor.submit(() -> {
					start.await();
					return myProfileService.changeProfile(id, request);
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

		List<Integer> slots = jdbcTemplate.queryForList("""
				SELECT e.slot FROM burrow_entrance e JOIN burrow b ON b.id = e.burrow_id
				WHERE b.user_id IN (%s) AND e.campus_floor_id = ?
				ORDER BY e.slot
				""".formatted(String.join(",", ids.stream().map(String::valueOf).toList())), Integer.class, floor5);
		assertThat(slots).doesNotHaveDuplicates().hasSize(MOVERS);
		assertThat(jdbcTemplate.queryForList(
				"SELECT campus_floor_id FROM user_profile WHERE user_id IN (%s)".formatted(
						String.join(",", ids.stream().map(String::valueOf).toList())), Long.class))
				.containsOnly(floor5);
	}

	/** 15기 · 서울 2층으로 가입하고 온보딩을 마친 계정. 끝나면 지운다. */
	private long onboard(String email, String nickname) {
		long userId = testUsers.createEmailUser(email, "mole1234!");
		userIds.add(userId);
		onboardingService.complete(userId, new OnboardingCompleteRequest(nickname,
				new OnboardingCompleteRequest.Mole(null, null, null, null), steps.interestIds(3),
				steps.questionIds(3).stream()
						.map(id -> new OnboardingCompleteRequest.Answer(id, "동시에 저장하는 사람의 답변"))
						.toList()));
		return userId;
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
