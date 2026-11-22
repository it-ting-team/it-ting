package com.itting.auth;

import static com.itting.support.TestUsers.lastSessionCookieHeader;
import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /api/v1/sessions}(로그인)와 {@code DELETE /api/v1/sessions/current}(로그아웃).
 * 계정은 테스트마다 DB에 넣고 트랜잭션이 끝나면 되돌린다. 세션은 Redis 컨테이너에 남는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SessionApiTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestUsers testUsers;

	@Autowired
	FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	@Test
	void 맞는_이메일과_비밀번호면_201과_다음_화면을_주고_세션_쿠키를_준다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);

		MockHttpServletResponse response = login("mole@example.com", PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(content().json("""
						{ "success": true, "data": { "userId": %d, "nextStep": "ONBOARDING" }, "error": null }
						""".formatted(userId), JsonCompareMode.STRICT))
				.andReturn().getResponse();

		assertThat(lastSessionCookieHeader(response))
				.contains("Max-Age=1209600", "Path=/", "Secure", "HttpOnly", "SameSite=Lax");
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie(response)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId));
	}

	@Test
	void 세션은_Redis에_계정_id로_색인되고_14일_동안_쓰지_않으면_사라진다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);

		login("mole@example.com", PASSWORD).andExpect(status().isCreated());

		var sessions = sessionRepository.findByPrincipalName(String.valueOf(userId));
		assertThat(sessions).hasSize(1);
		assertThat(sessions.values().iterator().next().getMaxInactiveInterval()).isEqualTo(Duration.ofDays(14));
	}

	@Test
	void 이메일은_대소문자를_가리지_않는다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);

		login("Mole@Example.com", PASSWORD).andExpect(status().isCreated());
	}

	@Test
	void 온보딩을_마친_계정은_HOME이다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		testUsers.markOnboarded(userId);

		login("mole@example.com", PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.nextStep").value("HOME"));
	}

	@Test
	void 비밀번호가_틀리거나_없는_이메일이거나_비밀번호가_없는_계정은_같은_401_LOGIN_FAILED다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		testUsers.createGoogleOnlyUser("google-mole@example.com");
		String loginFailed = """
				{
				  "success": false,
				  "data": null,
				  "error": { "code": "LOGIN_FAILED", "message": "이메일 또는 비밀번호가 맞지 않습니다.", "fields": null, "detail": null }
				}
				""";

		for (String[] attempt : new String[][] {
				{ "mole@example.com", "wrong1234!" },
				{ "nobody@example.com", PASSWORD },
				{ "google-mole@example.com", PASSWORD },
				{ "mole@example.com", PASSWORD + "a".repeat(80) } }) {
			MockHttpServletResponse response = login(attempt[0], attempt[1])
					.andExpect(status().isUnauthorized())
					.andExpect(content().json(loginFailed, JsonCompareMode.STRICT))
					.andReturn().getResponse();
			assertThat(lastSessionCookieHeader(response)).isNull();
		}
	}

	@Test
	void 탈퇴한_계정은_로그인되지_않는다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		testUsers.withdraw(userId);

		login("mole@example.com", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
	}

	@Test
	void 정지_중이면_403_ACCOUNT_SUSPENDED와_가장_늦게_끝나는_정지의_사유와_끝_시각을_준다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		long adminId = testUsers.createAdmin();
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		testUsers.suspend(userId, adminId, "신고 누적", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(7)), null);
		testUsers.suspend(userId, adminId, "욕설", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(3)), null);

		MockHttpServletResponse response = login("mole@example.com", PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "ACCOUNT_SUSPENDED",
						    "message": "정지된 계정입니다.",
						    "fields": null,
						    "detail": { "reason": "신고 누적", "endsAt": "%s" }
						  }
						}
						""".formatted(now.plus(Duration.ofDays(7))), JsonCompareMode.STRICT))
				.andReturn().getResponse();
		assertThat(lastSessionCookieHeader(response)).isNull();
	}

	@Test
	void 비밀번호가_틀리면_정지_중이어도_LOGIN_FAILED다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		long adminId = testUsers.createAdmin();
		Instant now = Instant.now();
		testUsers.suspend(userId, adminId, "신고 누적", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(7)), null);

		login("mole@example.com", "wrong1234!")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
	}

	@Test
	void 끝났거나_해제됐거나_아직_시작하지_않은_정지는_로그인을_막지_않는다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		long adminId = testUsers.createAdmin();
		Instant now = Instant.now();
		testUsers.suspend(userId, adminId, "끝남", now.minus(Duration.ofDays(8)), now.minus(Duration.ofDays(1)), null);
		testUsers.suspend(userId, adminId, "해제됨", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(7)),
				now.minus(Duration.ofHours(1)));
		testUsers.suspend(userId, adminId, "예정", now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(2)), null);

		login("mole@example.com", PASSWORD).andExpect(status().isCreated());
	}

	@Test
	void 이메일_형식이_틀리거나_비밀번호가_비면_400_INVALID_INPUT이다() throws Exception {
		mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content("""
				{ "email": "mole", "password": "" }
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(
						org.hamcrest.Matchers.containsInAnyOrder("email", "password")));
	}

	@Test
	void 로그인하면_이전_세션은_버리고_새_세션_id를_준다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie first = sessionCookie(login("mole@example.com", PASSWORD).andReturn().getResponse());

		Cookie second = sessionCookie(mockMvc.perform(post("/api/v1/sessions").cookie(first)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body("mole@example.com", PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse());

		assertThat(second.getValue()).isNotEqualTo(first.getValue());
		mockMvc.perform(get("/api/v1/me").cookie(first)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me").cookie(second)).andExpect(status().isOk());
	}

	@Test
	void 로그아웃하면_200과_쿠키를_지우는_헤더를_주고_그_세션은_더_쓸_수_없다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = sessionCookie(login("mole@example.com", PASSWORD).andReturn().getResponse());

		MockHttpServletResponse response = mockMvc.perform(delete("/api/v1/sessions/current").cookie(session))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT))
				.andReturn().getResponse();

		assertThat(lastSessionCookieHeader(response)).startsWith("ITTING_SESSION=;").contains("Max-Age=0");
		mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 로그아웃은_지금_기기의_세션만_지운다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie phone = sessionCookie(login("mole@example.com", PASSWORD).andReturn().getResponse());
		Cookie laptop = sessionCookie(login("mole@example.com", PASSWORD).andReturn().getResponse());

		mockMvc.perform(delete("/api/v1/sessions/current").cookie(phone)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/me").cookie(laptop)).andExpect(status().isOk());
	}

	@Test
	void 로그인하지_않고_로그아웃하면_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(delete("/api/v1/sessions/current"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": { "code": "UNAUTHENTICATED", "message": "로그인이 필요합니다.", "fields": null, "detail": null }
						}
						""", JsonCompareMode.STRICT));
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(email, password)));
	}

	private static String body(String email, String password) {
		return """
				{ "email": "%s", "password": "%s" }
				""".formatted(email, password);
	}

}
