package com.itting.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.itting.user.domain.User;
import com.itting.user.repository.UserRepository;
import com.itting.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 비밀번호 변경과 Google 연동 해제가 겹칠 때. 비밀번호 변경이 해제 전의 계정을 읽고 나중에 커밋해도, 해제한 Google 연동이 되살아나면 안
 * 된다({@code docs/api/10-settings.md}의 {@code DELETE /api/v1/me/google-link}). 테스트 트랜잭션 없이 실제로 커밋하므로 끝나면
 * 만든 행을 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PasswordChangeGoogleUnlinkRaceTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	TestUsers testUsers;

	@Autowired
	UserService userService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (Long userId : userIds) {
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
	}

	@Test
	void 해제_전에_읽은_계정의_비밀번호를_해제_뒤에_저장해도_연동이_되살아나지_않는다() throws Exception {
		long userId = linkedMember();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			transactionTemplate.executeWithoutResult(status -> {
				// 잠그지 않고 읽는다. 이 시점의 계정은 아직 연동돼 있다.
				User stale = userRepository.findById(userId).orElseThrow();
				assertThat(stale.getGoogleSub()).isNotNull();
				// 그 사이 다른 트랜잭션이 연동을 해제하고 커밋한다.
				Future<?> unlink = executor.submit(() -> userService.unlinkGoogle(userId));
				try {
					unlink.get(10, TimeUnit.SECONDS);
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
				stale.changePassword("$2a$10$changedchangedchangedchangedchangedchangedchangedchang");
			});
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(googleSub(userId)).isNull();
		assertThat(passwordHash(userId)).startsWith("$2a$10$changed");
	}

	@Test
	void 연동_해제가_계정을_잠그고_있으면_비밀번호_변경은_해제가_끝난_뒤에_저장한다() throws Exception {
		long userId = linkedMember();
		CountDownLatch locked = new CountDownLatch(1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> unlink = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
				User user = userRepository.findByIdForUpdate(userId).orElseThrow();
				locked.countDown();
				sleep(500);
				user.unlinkGoogle();
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

			assertThat(userService.changePassword(userId, "$2a$10$changedchangedchangedchangedchangedchangedchangedchang"))
					.isTrue();
			unlink.get(10, TimeUnit.SECONDS);
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(googleSub(userId)).isNull();
		assertThat(passwordHash(userId)).startsWith("$2a$10$changed");
	}

	/** 비밀번호가 있고 Google도 연동한 계정을 실제로 커밋한다. */
	private long linkedMember() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		long userId = testUsers.createEmailUser("race-" + suffix + "@example.com", PASSWORD);
		userIds.add(userId);
		jdbcTemplate.update("UPDATE `user` SET google_sub = ?, google_email = ? WHERE id = ?",
				"google-sub-race-" + suffix, "race-" + suffix + "@gmail.com", userId);
		return userId;
	}

	private String googleSub(long userId) {
		return jdbcTemplate.queryForObject("SELECT google_sub FROM `user` WHERE id = ?", String.class, userId);
	}

	private String passwordHash(long userId) {
		return jdbcTemplate.queryForObject("SELECT password_hash FROM `user` WHERE id = ?", String.class, userId);
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
