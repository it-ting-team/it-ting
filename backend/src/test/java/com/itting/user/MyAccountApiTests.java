package com.itting.user;

import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itting.support.NotificationSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/me/account}, {@code PATCH /api/v1/me/password}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MyAccountApiTests {

	private static final String PASSWORD = "mole1234!";

	private static final String NEW_PASSWORD = "newmole5678@";

	private static final String EMAIL = "account@example.com";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	TestUsers testUsers;

	@Autowired
	NotificationSteps steps;

	@PersistenceContext
	EntityManager entityManager;

	long userId;

	@BeforeEach
	void setUp() {
		userId = steps.createMember(EMAIL, PASSWORD, "두더지왕");
	}

	// GET /api/v1/me/account

	@Test
	void 이메일로_가입한_계정은_비밀번호가_있고_Google은_연결되지_않았다() throws Exception {
		mockMvc.perform(get("/api/v1/me/account").cookie(login(EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": {
						    "hasPassword": true,
						    "isGoogleLinked": false,
						    "googleEmail": null,
						    "canUnlinkGoogle": false
						  },
						  "error": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 이메일로_가입하고_Google을_연동한_계정은_연동을_해제할_수_있다() throws Exception {
		Cookie session = login(EMAIL, PASSWORD);
		linkGoogle("dawnwalk@gmail.com");

		mockMvc.perform(get("/api/v1/me/account").cookie(session))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": {
						    "hasPassword": true,
						    "isGoogleLinked": true,
						    "googleEmail": "dawnwalk@gmail.com",
						    "canUnlinkGoogle": true
						  },
						  "error": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void Google로만_가입한_계정은_비밀번호가_없고_연동을_해제할_수_없다() throws Exception {
		Cookie session = login(EMAIL, PASSWORD);
		linkGoogle("dawnwalk@gmail.com");
		removePassword();

		mockMvc.perform(get("/api/v1/me/account").cookie(session))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": {
						    "hasPassword": false,
						    "isGoogleLinked": true,
						    "googleEmail": "dawnwalk@gmail.com",
						    "canUnlinkGoogle": false
						  },
						  "error": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 계정_정보는_비밀번호나_Google_식별자나_로그인_이메일을_주지_않는다() throws Exception {
		Cookie session = login(EMAIL, PASSWORD);
		linkGoogle("dawnwalk@gmail.com");

		String body = mockMvc.perform(get("/api/v1/me/account").cookie(session))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(EMAIL).doesNotContain("google-dawnwalk").doesNotContain("$2");
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/account"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		changePassword(null, PASSWORD, NEW_PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 온보딩을_마치지_않은_계정은_403이다() throws Exception {
		testUsers.createEmailUser("new@example.com", PASSWORD);
		Cookie session = login("new@example.com", PASSWORD);

		mockMvc.perform(get("/api/v1/me/account").cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
		changePassword(session, PASSWORD, NEW_PASSWORD)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	// PATCH /api/v1/me/password

	@Test
	void 비밀번호를_바꾸면_새_비밀번호로_로그인되고_다른_기기는_로그아웃되고_지금_기기는_그대로다() throws Exception {
		Cookie current = login(EMAIL, PASSWORD);
		Cookie phone = login(EMAIL, PASSWORD);
		Cookie laptop = login(EMAIL, PASSWORD);

		changePassword(current, PASSWORD, NEW_PASSWORD)
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT));

		mockMvc.perform(get("/api/v1/me/account").cookie(current)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me/account").cookie(phone))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/v1/me/account").cookie(laptop)).andExpect(status().isUnauthorized());

		loginRequest(EMAIL, PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
		loginRequest(EMAIL, NEW_PASSWORD).andExpect(status().isCreated());

		String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM `user` WHERE id = ?", String.class,
				userId);
		assertThat(passwordEncoder.matches(NEW_PASSWORD, hash)).isTrue();
	}

	@Test
	void 지금_비밀번호가_틀리면_400_CURRENT_PASSWORD_MISMATCH이고_아무것도_바뀌지_않는다() throws Exception {
		Cookie current = login(EMAIL, PASSWORD);
		Cookie phone = login(EMAIL, PASSWORD);

		changePassword(current, "wrong1234!", NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"));
		// 지금 비밀번호를 모르면 새 비밀번호가 지금과 같은지도 알려 주지 않는다.
		changePassword(current, "wrong1234!", PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"));
		// BCrypt가 보는 길이(72바이트)를 넘는 값은 비교하지 않고 틀린 것으로 본다.
		changePassword(current, PASSWORD + "a".repeat(80), NEW_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"));

		loginRequest(EMAIL, PASSWORD).andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/me/account").cookie(phone)).andExpect(status().isOk());
	}

	@Test
	void 새_비밀번호가_지금과_같으면_400_PASSWORD_UNCHANGED이고_다른_기기는_로그아웃되지_않는다() throws Exception {
		Cookie current = login(EMAIL, PASSWORD);
		Cookie phone = login(EMAIL, PASSWORD);

		changePassword(current, PASSWORD, PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("PASSWORD_UNCHANGED"));

		mockMvc.perform(get("/api/v1/me/account").cookie(phone)).andExpect(status().isOk());
	}

	@Test
	void 비밀번호가_없는_계정은_409_PASSWORD_NOT_SET이다() throws Exception {
		Cookie session = login(EMAIL, PASSWORD);
		linkGoogle("dawnwalk@gmail.com");
		removePassword();

		changePassword(session, PASSWORD, NEW_PASSWORD)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("PASSWORD_NOT_SET"));
	}

	@Test
	void 새_비밀번호가_규칙에_어긋나면_400_INVALID_INPUT과_fields를_준다() throws Exception {
		Cookie session = login(EMAIL, PASSWORD);

		changePassword(session, PASSWORD, "short1!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("newPassword")));
		changePassword(session, PASSWORD, "onlyletters!")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("newPassword")));
		mockMvc.perform(patch("/api/v1/me/password").cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "currentPassword": "%s" }
								""".formatted(PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("newPassword")));
		mockMvc.perform(patch("/api/v1/me/password").cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "newPassword": "%s" }
								""".formatted(NEW_PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("currentPassword")));

		loginRequest(EMAIL, PASSWORD).andExpect(status().isCreated());
	}

	private void linkGoogle(String googleEmail) {
		jdbcTemplate.update("UPDATE `user` SET google_sub = ?, google_email = ? WHERE id = ?",
				"google-" + googleEmail, googleEmail, userId);
		entityManager.clear();
	}

	private void removePassword() {
		jdbcTemplate.update("UPDATE `user` SET password_hash = NULL WHERE id = ?", userId);
		entityManager.clear();
	}

	private ResultActions changePassword(Cookie session, String currentPassword, String newPassword) throws Exception {
		var request = patch("/api/v1/me/password")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "currentPassword": "%s", "newPassword": "%s" }
						""".formatted(currentPassword, newPassword));
		return mockMvc.perform(session == null ? request : request.cookie(session));
	}

	private ResultActions loginRequest(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "email": "%s", "password": "%s" }
						""".formatted(email, password)));
	}

	private Cookie login(String email, String password) throws Exception {
		return sessionCookie(loginRequest(email, password)
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

}
