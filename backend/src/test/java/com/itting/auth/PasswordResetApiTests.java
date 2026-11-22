package com.itting.auth;

import static com.itting.support.SignupSteps.newEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.itting.support.Mailpit;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.SignupSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code /api/v1/password-resets}. 메일은 응답 뒤에 따로 보내므로 Mailpit에 올 때까지 기다린다.
 */
@SpringBootTest(properties = "itting.generations=15,16,17,18,19,20,21")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class PasswordResetApiTests {

	private static final String PASSWORD = "mole1234!";

	private static final String NEW_PASSWORD = "newmole5678@";

	private static final String LINK_INVALID = """
			{
			  "success": false,
			  "data": null,
			  "error": {
			    "code": "PASSWORD_RESET_LINK_INVALID",
			    "message": "만료됐거나 이미 쓴 링크입니다. 재설정 메일을 다시 받아 주세요.",
			    "fields": null,
			    "detail": null
			  }
			}
			""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	Mailpit mailpit;

	@Autowired
	MutableClock clock;

	@Autowired
	TestUsers testUsers;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		clock.setNow(Instant.now().truncatedTo(ChronoUnit.SECONDS));
	}

	@Test
	void 가입한_이메일이면_201을_주고_재설정_링크_메일을_보낸다() throws Exception {
		String email = newEmail();
		testUsers.createEmailUser(email, PASSWORD);

		requestReset(email)
				.andExpect(status().isCreated())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT));

		Mailpit.Message mail = mailpit.awaitLatestTo(email, 1);
		assertThat(mail.subject()).isEqualTo("[디굴디굴] 비밀번호 재설정");
		assertThat(mail.link()).startsWith("http://localhost:3000/password-reset/");
		assertThat(mail.text()).contains("링크는 30분 동안 한 번만 쓸 수 있어요.");
		mockMvc.perform(get("/api/v1/password-resets/" + token(mail)))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 없는_이메일_Google로만_가입_탈퇴한_계정도_같은_201이고_메일은_보내지_않는다() throws Exception {
		String nobody = newEmail();
		String googleOnly = newEmail();
		testUsers.createGoogleOnlyUser(googleOnly);
		String withdrawn = newEmail();
		testUsers.withdraw(testUsers.createEmailUser(withdrawn, PASSWORD));
		String member = newEmail();
		testUsers.createEmailUser(member, PASSWORD);

		for (String email : List.of(nobody, googleOnly, withdrawn, member)) {
			requestReset(email)
					.andExpect(status().isCreated())
					.andExpect(content().json("""
							{ "success": true, "data": null, "error": null }
							""", JsonCompareMode.STRICT));
		}

		// 가입한 이메일의 메일이 온 뒤에도 나머지에는 오지 않았다.
		mailpit.awaitLatestTo(member, 1);
		Thread.sleep(500);
		assertThat(mailpit.countTo(nobody)).isZero();
		assertThat(mailpit.countTo(googleOnly)).isZero();
		assertThat(mailpit.countTo(withdrawn)).isZero();
	}

	@Test
	void 이메일_형식이_틀리면_400_INVALID_INPUT이다() throws Exception {
		requestReset("mole")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("email"));
	}

	@Test
	void 간격_안에_다시_요청하면_같은_201이지만_메일을_보내지_않고_지나면_새_링크만_쓸_수_있다() throws Exception {
		String email = newEmail();
		testUsers.createEmailUser(email, PASSWORD);
		requestReset(email).andExpect(status().isCreated());
		String first = token(mailpit.awaitLatestTo(email, 1));

		clock.advance(Duration.ofSeconds(59));
		requestReset(email).andExpect(status().isCreated());
		Thread.sleep(500);
		assertThat(mailpit.countTo(email)).isEqualTo(1);

		clock.advance(Duration.ofSeconds(1));
		requestReset(email).andExpect(status().isCreated());
		String second = token(mailpit.awaitLatestTo(email, 2));

		mockMvc.perform(get("/api/v1/password-resets/" + first))
				.andExpect(status().isNotFound())
				.andExpect(content().json(LINK_INVALID, JsonCompareMode.STRICT));
		mockMvc.perform(get("/api/v1/password-resets/" + second)).andExpect(status().isOk());
	}

	@Test
	void 대소문자나_악센트만_다른_이메일로_다시_요청해도_같은_계정이면_간격_안에는_메일을_보내지_않는다() throws Exception {
		String email = newEmail();
		testUsers.createEmailUser(email, PASSWORD);
		requestReset(email).andExpect(status().isCreated());
		String first = token(mailpit.awaitLatestTo(email, 1));

		requestReset(email.toUpperCase()).andExpect(status().isCreated());
		requestReset(email.replaceFirst("o", "ó")).andExpect(status().isCreated());
		Thread.sleep(500);

		assertThat(mailpit.countTo(email)).isEqualTo(1);
		mockMvc.perform(get("/api/v1/password-resets/" + first)).andExpect(status().isOk());
	}

	@Test
	void 새_비밀번호를_저장하면_새_비밀번호로만_로그인되고_모든_기기에서_로그아웃되며_링크는_다시_쓸_수_없다() throws Exception {
		String email = newEmail();
		testUsers.createEmailUser(email, PASSWORD);
		Cookie phone = login(email, PASSWORD);
		Cookie laptop = login(email, PASSWORD);
		requestReset(email).andExpect(status().isCreated());
		String token = token(mailpit.awaitLatestTo(email, 1));

		complete(token, NEW_PASSWORD)
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT));

		mockMvc.perform(get("/api/v1/me").cookie(phone)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me").cookie(laptop)).andExpect(status().isUnauthorized());
		loginRequest(email, PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
		loginRequest(email, NEW_PASSWORD).andExpect(status().isCreated());
		complete(token, "another1234!")
				.andExpect(status().isNotFound())
				.andExpect(content().json(LINK_INVALID, JsonCompareMode.STRICT));
		mockMvc.perform(get("/api/v1/password-resets/" + token)).andExpect(status().isNotFound());
	}

	@Test
	void 새_비밀번호가_규칙에_맞지_않으면_400이고_링크는_남는다() throws Exception {
		String email = newEmail();
		testUsers.createEmailUser(email, PASSWORD);
		requestReset(email).andExpect(status().isCreated());
		String token = token(mailpit.awaitLatestTo(email, 1));

		complete(token, "short1!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("password"))
				.andExpect(jsonPath("$.error.fields[0].message").value("8자 이상 20자 이하여야 합니다."));

		mockMvc.perform(get("/api/v1/password-resets/" + token)).andExpect(status().isOk());
		complete(token, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 삼십_분이_지난_링크는_404다() throws Exception {
		String email = newEmail();
		testUsers.createEmailUser(email, PASSWORD);
		requestReset(email).andExpect(status().isCreated());
		String token = token(mailpit.awaitLatestTo(email, 1));

		clock.advance(Duration.ofMinutes(30));

		mockMvc.perform(get("/api/v1/password-resets/" + token))
				.andExpect(status().isNotFound())
				.andExpect(content().json(LINK_INVALID, JsonCompareMode.STRICT));
		complete(token, NEW_PASSWORD).andExpect(status().isNotFound());
	}

	@Test
	void 없는_링크는_404다() throws Exception {
		mockMvc.perform(get("/api/v1/password-resets/no-such-link"))
				.andExpect(status().isNotFound())
				.andExpect(content().json(LINK_INVALID, JsonCompareMode.STRICT));
		complete("no-such-link", NEW_PASSWORD)
				.andExpect(status().isNotFound())
				.andExpect(content().json(LINK_INVALID, JsonCompareMode.STRICT));
	}

	/** 다른 스레드의 요청이 계정을 볼 수 있게 트랜잭션 밖에서 넣고, 끝나면 지운다. */
	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 같은_링크로_동시에_저장해도_한_번만_바뀐다() throws Exception {
		String email = newEmail();
		long userId = testUsers.createEmailUser(email, PASSWORD);
		try {
			requestReset(email).andExpect(status().isCreated());
			String token = token(mailpit.awaitLatestTo(email, 1));

			List<Integer> statuses = SignupSteps.concurrently(5,
					() -> complete(token, NEW_PASSWORD).andReturn().getResponse().getStatus());

			assertThat(statuses).containsOnlyOnce(200).containsOnly(200, 404);
		}
		finally {
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
	}

	@Test
	void 정지된_계정도_재설정_메일을_받는다() throws Exception {
		String email = newEmail();
		long userId = testUsers.createEmailUser(email, PASSWORD);
		Instant now = Instant.now();
		testUsers.suspend(userId, testUsers.createAdmin(), "신고 누적", now.minus(Duration.ofDays(1)),
				now.plus(Duration.ofDays(7)), null);

		requestReset(email).andExpect(status().isCreated());

		assertThat(mailpit.awaitLatestTo(email, 1).link()).contains("/password-reset/");
	}

	private ResultActions requestReset(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/password-resets")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"" + email + "\" }"));
	}

	private ResultActions complete(String token, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/password-resets/" + token + "/complete")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"password\": \"" + password + "\" }"));
	}

	private ResultActions loginRequest(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"" + email + "\", \"password\": \"" + password + "\" }"));
	}

	private Cookie login(String email, String password) throws Exception {
		return TestUsers.sessionCookie(loginRequest(email, password)
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

	private static String token(Mailpit.Message mail) {
		String link = mail.link();
		return link.substring(link.lastIndexOf('/') + 1);
	}

}
