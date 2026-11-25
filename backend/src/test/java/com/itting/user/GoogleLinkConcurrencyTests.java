package com.itting.user;

import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.itting.auth.domain.GoogleAccount;
import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.auth.service.GoogleVerificationService;
import com.itting.support.GoogleSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 서로 다른 두 계정이 같은 Google 계정을 동시에 연동할 때. 둘 다 확인을 받았으면 {@code user.google_sub}의 UNIQUE가
 * 한쪽만 통과시키고, 진 쪽은 {@code 409 GOOGLE_ACCOUNT_DUPLICATED}를 받는다. 테스트 트랜잭션 없이 실제로 커밋하므로 끝나면 만든
 * 행을 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GoogleLinkConcurrencyTests {

	private static final String PASSWORD = "mole1234!";

	private static final int ROUNDS = 5;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestUsers testUsers;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	GoogleVerificationService verifications;

	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		for (Long userId : userIds) {
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
	}

	@Test
	void 같은_Google_계정을_두_계정이_동시에_연동하면_한쪽만_연결되고_진_쪽은_409다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			String suffix = UUID.randomUUID().toString().substring(0, 8);
			GoogleAccount account = new GoogleAccount("google-sub-race-" + suffix, "race-" + suffix + "@gmail.com");
			long first = member("first-" + suffix + "@example.com");
			long second = member("second-" + suffix + "@example.com");
			Cookie firstSession = login("first-" + suffix + "@example.com");
			Cookie secondSession = login("second-" + suffix + "@example.com");
			// 두 계정이 모두 같은 Google 계정을 확인받은 상태다(콜백의 겹침 검사는 둘 다 통과했다).
			verifications.save(GoogleSteps.sessionId(firstSession), GoogleOAuthPurpose.LINK, account);
			verifications.save(GoogleSteps.sessionId(secondSession), GoogleOAuthPurpose.LINK, account);

			List<MockHttpServletResponse> responses = concurrently(List.of(
					() -> link(firstSession),
					() -> link(secondSession)));

			List<Integer> statuses = responses.stream().map(MockHttpServletResponse::getStatus).sorted().toList();
			assertThat(statuses).as("%d번째", round + 1).containsExactly(200, 409);
			MockHttpServletResponse loser = responses.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
			assertThat((String) JsonPath.read(loser.getContentAsString(), "$.error.code"))
					.isEqualTo("GOOGLE_ACCOUNT_DUPLICATED");
			assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `user` WHERE google_sub = ?", Integer.class,
					account.sub())).isEqualTo(1);
			assertThat(jdbcTemplate.queryForObject("""
					SELECT COUNT(*) FROM `user` WHERE id IN (?, ?) AND google_sub IS NOT NULL
					""", Integer.class, first, second)).isEqualTo(1);
		}
	}

	private MockHttpServletResponse link(Cookie session) throws Exception {
		return mockMvc.perform(put("/api/v1/me/google-link").cookie(session)).andReturn().getResponse();
	}

	/** 온보딩을 마친 이메일 계정을 실제로 커밋한다. */
	private long member(String email) {
		long userId = testUsers.createEmailUser(email, PASSWORD);
		userIds.add(userId);
		testUsers.markOnboarded(userId);
		return userId;
	}

	private Cookie login(String email) throws Exception {
		return sessionCookie(mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\" }"))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

	/** 요청을 여러 스레드에서 한꺼번에 시작하고 응답을 모은다. */
	private static List<MockHttpServletResponse> concurrently(List<Callable<MockHttpServletResponse>> requests)
			throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(requests.size());
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
			for (Callable<MockHttpServletResponse> request : requests) {
				futures.add(executor.submit(() -> {
					start.await();
					return request.call();
				}));
			}
			start.countDown();
			List<MockHttpServletResponse> responses = new ArrayList<>();
			for (Future<MockHttpServletResponse> future : futures) {
				responses.add(future.get());
			}
			return responses;
		}
		finally {
			executor.shutdownNow();
		}
	}

}
