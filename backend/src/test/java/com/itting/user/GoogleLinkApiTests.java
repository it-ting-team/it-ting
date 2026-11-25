package com.itting.user;

import static com.itting.support.GoogleSteps.location;
import static com.itting.support.GoogleSteps.sessionId;
import static com.itting.support.TestUsers.googleSub;
import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.auth.service.GoogleVerificationService;
import com.itting.support.FakeGoogle;
import com.itting.support.GoogleSteps;
import com.itting.support.GoogleSteps.Attempt;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PUT /api/v1/me/google-link}, {@code DELETE /api/v1/me/google-link}. 확인은 실제 {@code verify}와 콜백을 불러 받고
 * Google은 {@link FakeGoogle}이 맡는다. 같은 Google 계정을 두 계정이 동시에 연동하는 경우는
 * {@code GoogleLinkConcurrencyTests}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class GoogleLinkApiTests {

	private static final String PASSWORD = "mole1234!";

	private static final String EMAIL = "mole@example.com";

	private static final String GOOGLE_EMAIL = "dawnwalk@gmail.com";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeGoogle fakeGoogle;

	@Autowired
	MutableClock clock;

	@Autowired
	TestUsers testUsers;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	GoogleVerificationService verifications;

	@PersistenceContext
	EntityManager entityManager;

	GoogleSteps google;

	long userId;

	@BeforeEach
	void setUp() {
		clock.setNow(Instant.now().truncatedTo(ChronoUnit.SECONDS));
		fakeGoogle.clear();
		google = new GoogleSteps(mockMvc, fakeGoogle);
		userId = testUsers.createEmailUser(EMAIL, PASSWORD);
		testUsers.markOnboarded(userId);
	}

	// PUT /api/v1/me/google-link

	@Test
	void 확인받은_Google_계정을_연결하고_연결한_뒤의_계정_정보를_주고_확인_기록을_지운다() throws Exception {
		Cookie session = login();
		verify(session, GOOGLE_EMAIL);

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
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

		entityManager.flush();
		Map<String, Object> user = jdbcTemplate.queryForMap("SELECT google_sub, google_email FROM `user` WHERE id = ?",
				userId);
		assertThat(user.get("google_sub")).isEqualTo(googleSub(GOOGLE_EMAIL));
		assertThat(user.get("google_email")).isEqualTo(GOOGLE_EMAIL);
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isEmpty();
		mockMvc.perform(get("/api/v1/me/account").cookie(session))
				.andExpect(jsonPath("$.data.isGoogleLinked").value(true));
	}

	@Test
	void 연동한_뒤에는_그_Google_계정으로_로그인된다() throws Exception {
		Cookie session = login();
		verify(session, GOOGLE_EMAIL);
		mockMvc.perform(put("/api/v1/me/google-link").cookie(session)).andExpect(status().isOk());

		MockHttpServletResponse response = google.loginWith(googleSub(GOOGLE_EMAIL), GOOGLE_EMAIL)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo("http://localhost:3000/");
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie(response)))
				.andExpect(jsonPath("$.data.userId").value(userId));
	}

	@Test
	void 확인_기록은_한_번만_쓴다_같은_요청을_다시_불러도_이미_연동된_계정은_그대로_성공이다() throws Exception {
		Cookie session = login();
		verify(session, GOOGLE_EMAIL);
		mockMvc.perform(put("/api/v1/me/google-link").cookie(session)).andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isGoogleLinked").value(true))
				.andExpect(jsonPath("$.data.googleEmail").value(GOOGLE_EMAIL));
	}

	@Test
	void 확인_기록이_없고_이미_연동된_계정은_바꾸지_않고_성공이다() throws Exception {
		testUsers.linkGoogle(userId, "linked@gmail.com");
		Cookie session = login();

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isGoogleLinked").value(true))
				.andExpect(jsonPath("$.data.googleEmail").value("linked@gmail.com"));

		assertThat(jdbcTemplate.queryForObject("SELECT google_sub FROM `user` WHERE id = ?", String.class, userId))
				.isEqualTo(googleSub("linked@gmail.com"));
	}

	@Test
	void 확인_기록이_없고_연동되지_않은_계정은_409_GOOGLE_VERIFICATION_REQUIRED다() throws Exception {
		Cookie session = login();

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isConflict())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "GOOGLE_VERIFICATION_REQUIRED",
						    "message": "Google 계정 확인이 필요합니다.",
						    "fields": null,
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));

		assertThat(jdbcTemplate.queryForObject("SELECT google_sub FROM `user` WHERE id = ?", String.class, userId))
				.isNull();
	}

	@Test
	void 확인받은_지_10분이_지나면_409_GOOGLE_VERIFICATION_REQUIRED다() throws Exception {
		Cookie session = login();
		verify(session, GOOGLE_EMAIL);

		clock.advance(Duration.ofMinutes(10));

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_VERIFICATION_REQUIRED"));
		assertThat(jdbcTemplate.queryForObject("SELECT google_sub FROM `user` WHERE id = ?", String.class, userId))
				.isNull();
	}

	@Test
	void 확인받은_지_10분이_되기_전에는_연결된다() throws Exception {
		Cookie session = login();
		verify(session, GOOGLE_EMAIL);

		clock.advance(Duration.ofMinutes(10).minusSeconds(1));

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void 다시_확인받으면_나중_기록으로_연결된다() throws Exception {
		Cookie session = login();
		verify(session, "first@gmail.com");
		verify(session, "second@gmail.com");

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.googleEmail").value("second@gmail.com"));
	}

	@Test
	void 다른_세션에서_확인받은_기록은_쓸_수_없다() throws Exception {
		Cookie started = login();
		Cookie another = login();
		verify(started, GOOGLE_EMAIL);

		mockMvc.perform(put("/api/v1/me/google-link").cookie(another))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_VERIFICATION_REQUIRED"));
		// 확인받은 세션에서는 그대로 쓸 수 있다.
		mockMvc.perform(put("/api/v1/me/google-link").cookie(started)).andExpect(status().isOk());
	}

	@Test
	void 탈퇴_확인_기록으로는_연동할_수_없고_그_기록을_지우지도_않는다() throws Exception {
		testUsers.linkGoogle(userId, GOOGLE_EMAIL);
		Cookie session = login();
		Attempt attempt = google.startVerify(session, "WITHDRAW");
		google.callback(attempt, google.codeFor(attempt, googleSub(GOOGLE_EMAIL), GOOGLE_EMAIL), session)
				.andExpect(status().isFound());
		jdbcTemplate.update("UPDATE `user` SET google_sub = NULL, google_email = NULL WHERE id = ?", userId);
		entityManager.clear();

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_VERIFICATION_REQUIRED"));

		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.WITHDRAW)).isPresent();
	}

	@Test
	void 확인받은_뒤_다른_계정이_그_Google_계정으로_가입했으면_409_GOOGLE_ACCOUNT_DUPLICATED다() throws Exception {
		Cookie session = login();
		verify(session, GOOGLE_EMAIL);
		testUsers.createGoogleOnlyUser(GOOGLE_EMAIL);

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("GOOGLE_ACCOUNT_DUPLICATED"));

		assertThat(jdbcTemplate.queryForObject("SELECT google_sub FROM `user` WHERE id = ?", String.class, userId))
				.isNull();
		// 이 확인 기록으로는 더 할 일이 없어 지운다. 다시 연동하려면 다시 확인받는다.
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isEmpty();
	}

	@Test
	void 확인받은_뒤_다른_Google_계정이_연결됐으면_409_GOOGLE_ALREADY_LINKED다() throws Exception {
		Cookie session = login();
		verify(session, GOOGLE_EMAIL);
		testUsers.linkGoogle(userId, "linked@gmail.com");

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_ALREADY_LINKED"));

		assertThat(jdbcTemplate.queryForObject("SELECT google_email FROM `user` WHERE id = ?", String.class, userId))
				.isEqualTo("linked@gmail.com");
	}

	@Test
	void 확인받은_뒤_같은_Google_계정이_이미_연결됐으면_바꾸지_않고_성공이다() throws Exception {
		Cookie session = login();
		verify(session, GOOGLE_EMAIL);
		testUsers.linkGoogle(userId, GOOGLE_EMAIL);

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.googleEmail").value(GOOGLE_EMAIL));
	}

	@Test
	void 연동은_로그인이_필요하다() throws Exception {
		mockMvc.perform(put("/api/v1/me/google-link"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(delete("/api/v1/me/google-link"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 온보딩을_마치지_않은_계정은_403이다() throws Exception {
		testUsers.createEmailUser("new@example.com", PASSWORD);
		Cookie session = login("new@example.com");

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
		mockMvc.perform(delete("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	@Test
	void 탈퇴한_계정의_세션은_세션을_지우고_401이다() throws Exception {
		Cookie session = login();
		verify(session, GOOGLE_EMAIL);
		testUsers.withdraw(userId);

		mockMvc.perform(put("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/v1/me/account").cookie(session)).andExpect(status().isUnauthorized());
	}

	// DELETE /api/v1/me/google-link

	@Test
	void 연동된_이메일_계정은_해제하고_해제한_뒤의_계정_정보를_주며_세션은_남는다() throws Exception {
		testUsers.linkGoogle(userId, GOOGLE_EMAIL);
		Cookie session = login();

		mockMvc.perform(delete("/api/v1/me/google-link").cookie(session))
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

		Map<String, Object> user = jdbcTemplate.queryForMap("SELECT google_sub, google_email FROM `user` WHERE id = ?",
				userId);
		assertThat(user.get("google_sub")).isNull();
		assertThat(user.get("google_email")).isNull();
		// 로그인 세션은 지우지 않는다.
		mockMvc.perform(get("/api/v1/me/account").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isGoogleLinked").value(false));
	}

	@Test
	void 해제한_뒤에는_그_Google_계정으로_로그인할_수_없고_처음_쓰는_계정이_된다() throws Exception {
		testUsers.linkGoogle(userId, GOOGLE_EMAIL);
		Cookie session = login();
		mockMvc.perform(delete("/api/v1/me/google-link").cookie(session)).andExpect(status().isOk());

		MockHttpServletResponse response = google.loginWith(googleSub(GOOGLE_EMAIL), GOOGLE_EMAIL)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo("http://localhost:3000/signup/ssafy?method=google");
	}

	@Test
	void 연동되지_않은_계정은_바꾸지_않고_성공이다() throws Exception {
		Cookie session = login();

		mockMvc.perform(delete("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.hasPassword").value(true))
				.andExpect(jsonPath("$.data.isGoogleLinked").value(false))
				.andExpect(jsonPath("$.data.googleEmail").doesNotExist())
				.andExpect(jsonPath("$.data.canUnlinkGoogle").value(false));
	}

	@Test
	void 두_번_불러도_같다() throws Exception {
		testUsers.linkGoogle(userId, GOOGLE_EMAIL);
		Cookie session = login();

		mockMvc.perform(delete("/api/v1/me/google-link").cookie(session)).andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isGoogleLinked").value(false));
	}

	@Test
	void Google로만_가입한_계정은_409_GOOGLE_UNLINK_NOT_ALLOWED이고_세션과_연동은_그대로다() throws Exception {
		long googleOnly = testUsers.createGoogleOnlyUser("only@gmail.com");
		testUsers.markOnboarded(googleOnly);
		Cookie session = sessionCookie(google.loginWith(googleSub("only@gmail.com"), "only@gmail.com")
				.andExpect(status().isFound()).andReturn().getResponse());

		mockMvc.perform(delete("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isConflict())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "GOOGLE_UNLINK_NOT_ALLOWED",
						    "message": "비밀번호가 없는 계정은 Google 연동을 해제할 수 없습니다.",
						    "fields": null,
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));

		assertThat(jdbcTemplate.queryForObject("SELECT google_sub FROM `user` WHERE id = ?", String.class, googleOnly))
				.isEqualTo(googleSub("only@gmail.com"));
		mockMvc.perform(get("/api/v1/me/account").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasPassword").value(false))
				.andExpect(jsonPath("$.data.canUnlinkGoogle").value(false));
	}

	@Test
	void 탈퇴한_계정의_세션으로_해제하면_401이다() throws Exception {
		testUsers.linkGoogle(userId, GOOGLE_EMAIL);
		Cookie session = login();
		testUsers.withdraw(userId);

		mockMvc.perform(delete("/api/v1/me/google-link").cookie(session))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	/** 이 세션에서 Google 확인(LINK)을 받는다. 시작 주소와 콜백을 실제로 부른다. */
	private void verify(Cookie session, String googleEmail) throws Exception {
		Attempt attempt = google.startVerify(session, "LINK");
		MockHttpServletResponse response = google
				.callback(attempt, google.codeFor(attempt, googleSub(googleEmail), googleEmail), session)
				.andExpect(status().isFound())
				.andReturn().getResponse();
		assertThat(location(response)).isEqualTo("http://localhost:3000/settings/google?verified=true");
	}

	private Cookie login() throws Exception {
		return login(EMAIL);
	}

	private Cookie login(String email) throws Exception {
		return sessionCookie(mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\" }"))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

}
