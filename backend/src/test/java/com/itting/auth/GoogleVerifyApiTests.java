package com.itting.auth;

import static com.itting.support.GoogleSteps.OAUTH_COOKIE;
import static com.itting.support.GoogleSteps.hasCookie;
import static com.itting.support.GoogleSteps.location;
import static com.itting.support.GoogleSteps.query;
import static com.itting.support.GoogleSteps.sessionId;
import static com.itting.support.GoogleSteps.withoutQuery;
import static com.itting.support.SignupSteps.lastCookieHeader;
import static com.itting.support.TestUsers.googleSub;
import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.auth.domain.GoogleVerification;
import com.itting.auth.service.GoogleVerificationService;
import com.itting.support.FakeGoogle;
import com.itting.support.GoogleSteps;
import com.itting.support.GoogleSteps.Attempt;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/oauth/google/verify}와 콜백의 확인 갈래(설정의 {@code LINK}, {@code WITHDRAW}). 콜백은 확인 기록만
 * Redis에 남기고 DB를 바꾸지 않는다. 기록을 쓰는 {@code PUT /api/v1/me/google-link}는 {@code GoogleLinkApiTests}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class GoogleVerifyApiTests {

	private static final String FRONTEND = "http://localhost:3000";

	private static final String PASSWORD = "mole1234!";

	private static final String EMAIL = "dawnwalk@gmail.com";

	private static final String LINK_FAILED = FRONTEND + "/settings/google?error=GOOGLE_VERIFICATION_FAILED";

	private static final String WITHDRAW_FAILED = FRONTEND + "/settings/leave?error=GOOGLE_VERIFICATION_FAILED";

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
	StringRedisTemplate redis;

	@Autowired
	GoogleVerificationService verifications;

	GoogleSteps google;

	@BeforeEach
	void setUp() {
		clock.setNow(Instant.now().truncatedTo(ChronoUnit.SECONDS));
		fakeGoogle.clear();
		google = new GoogleSteps(mockMvc, fakeGoogle);
	}

	// GET /api/v1/oauth/google/verify

	@Test
	void LINK로_시작하면_Google_로그인_화면으로_보내고_state와_PKCE와_쿠키를_준다() throws Exception {
		member("mole@example.com");
		Cookie session = login("mole@example.com");

		MockHttpServletResponse response = google.verify(session, "LINK")
				.andExpect(status().isFound())
				.andReturn().getResponse();

		String location = location(response);
		assertThat(withoutQuery(location)).isEqualTo(fakeGoogle.authorizationUri());
		Map<String, String> query = query(location);
		assertThat(query).containsEntry("client_id", FakeGoogle.CLIENT_ID)
				.containsEntry("redirect_uri", FakeGoogle.REDIRECT_URI)
				.containsEntry("scope", "openid email")
				.containsEntry("code_challenge_method", "S256");
		assertThat(query.get("state")).matches("[A-Za-z0-9_-]{43}");
		assertThat(query).doesNotContainKey("prompt");
		assertThat(lastCookieHeader(response, OAUTH_COOKIE))
				.startsWith(OAUTH_COOKIE + "=" + query.get("state") + ";")
				.contains("Max-Age=600", "Path=/api/v1/oauth/google", "HttpOnly", "SameSite=Lax");
		// 세션은 바꾸지 않는다.
		assertSessionKept(response, session);
	}

	@Test
	void WITHDRAW도_같은_길로_Google에_보낸다() throws Exception {
		testUsers.markOnboarded(testUsers.createGoogleOnlyUser(EMAIL));
		Cookie session = googleLogin();

		MockHttpServletResponse response = google.verify(session, "WITHDRAW")
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(withoutQuery(location(response))).isEqualTo(fakeGoogle.authorizationUri());
		assertThat(hasCookie(response, OAUTH_COOKIE)).isTrue();
	}

	@Test
	void 로그인_세션이_없으면_로그인_화면으로_보내고_시도를_만들지_않는다() throws Exception {
		MockHttpServletResponse response = google.verify(null, "LINK")
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/login");
		assertThat(hasCookie(response, OAUTH_COOKIE)).isFalse();
		// purpose가 틀려도 로그인이 먼저다.
		assertThat(location(google.verify(null, "nothing").andReturn().getResponse())).isEqualTo(FRONTEND + "/login");
	}

	@Test
	void purpose가_LINK나_WITHDRAW가_아니면_설정의_첫_화면으로_보낸다() throws Exception {
		member("mole@example.com");
		Cookie session = login("mole@example.com");

		for (String purpose : new String[] { null, "", "LOGIN", "link", "Withdraw", "LINK ", "UNLINK" }) {
			MockHttpServletResponse response = google.verify(session, purpose)
					.andExpect(status().isFound())
					.andReturn().getResponse();

			assertThat(location(response)).as("purpose=%s", purpose).isEqualTo(FRONTEND + "/settings");
			assertThat(hasCookie(response, OAUTH_COOKIE)).isFalse();
		}
	}

	@Test
	void 온보딩을_마치지_않은_계정은_403_ONBOARDING_INCOMPLETE다() throws Exception {
		testUsers.createEmailUser("new@example.com", PASSWORD);
		Cookie session = login("new@example.com");

		google.verify(session, "LINK")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	@Test
	void 계정이_없어진_세션은_세션을_지우고_로그인_화면으로_보낸다() throws Exception {
		long userId = member("mole@example.com");
		Cookie session = login("mole@example.com");
		testUsers.withdraw(userId);

		MockHttpServletResponse response = google.verify(session, "LINK")
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/login");
		assertThat(hasCookie(response, OAUTH_COOKIE)).isFalse();
	}

	// GET /api/v1/oauth/google/callback: LINK

	@Test
	void LINK_확인이_맞으면_연동_화면으로_보내고_확인_기록만_남기고_DB는_그대로다() throws Exception {
		long userId = member("mole@example.com");
		Cookie session = login("mole@example.com");
		Attempt attempt = google.startVerify(session, "LINK");
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse response = google.callback(attempt, code, session)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/settings/google?verified=true");
		// 로그인은 그대로이고 새 세션을 만들지 않는다. 시도 쿠키는 쓰고 지운다.
		assertSessionKept(response, session);
		assertThat(lastCookieHeader(response, OAUTH_COOKIE)).contains("Max-Age=0");
		Optional<GoogleVerification> record = verifications.find(sessionId(session), GoogleOAuthPurpose.LINK);
		assertThat(record).isPresent();
		assertThat(record.get().googleSub()).isEqualTo(googleSub(EMAIL));
		assertThat(record.get().googleEmail()).isEqualTo(EMAIL);
		assertThat(record.get().expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(10)));
		assertThat(redis.getExpire("itting:google-verification:" + sessionId(session))).isBetween(590L, 600L);
		Map<String, Object> user = jdbcTemplate.queryForMap("SELECT google_sub, google_email FROM `user` WHERE id = ?",
				userId);
		assertThat(user.get("google_sub")).isNull();
		assertThat(user.get("google_email")).isNull();
	}

	@Test
	void 같은_세션에서_다시_확인받으면_앞의_기록을_덮어쓴다() throws Exception {
		member("mole@example.com");
		Cookie session = login("mole@example.com");

		verifyLink(session, "first@gmail.com");
		verifyLink(session, "second@gmail.com");

		Optional<GoogleVerification> record = verifications.find(sessionId(session), GoogleOAuthPurpose.LINK);
		assertThat(record).isPresent();
		assertThat(record.get().googleEmail()).isEqualTo("second@gmail.com");
	}

	@Test
	void 확인_기록은_10분_뒤_쓸_수_없다() throws Exception {
		member("mole@example.com");
		Cookie session = login("mole@example.com");
		verifyLink(session, EMAIL);

		clock.advance(Duration.ofMinutes(10).minusSeconds(1));
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isPresent();
		clock.advance(Duration.ofSeconds(1));

		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isEmpty();
	}

	@Test
	void LINK인데_이미_Google_계정이_연결된_계정이면_GOOGLE_ALREADY_LINKED로_기록을_만들지_않는다() throws Exception {
		long userId = member("mole@example.com");
		testUsers.linkGoogle(userId, "linked@gmail.com");
		Cookie session = login("mole@example.com");
		Attempt attempt = google.startVerify(session, "LINK");
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse response = google.callback(attempt, code, session).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/settings/google?error=GOOGLE_ALREADY_LINKED");
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isEmpty();
	}

	@Test
	void LINK인데_다른_계정이_쓰는_Google_계정이면_GOOGLE_ACCOUNT_DUPLICATED로_기록을_만들지_않는다() throws Exception {
		member("mole@example.com");
		testUsers.createGoogleOnlyUser(EMAIL);
		Cookie session = login("mole@example.com");
		Attempt attempt = google.startVerify(session, "LINK");
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse response = google.callback(attempt, code, session).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/settings/google?error=GOOGLE_ACCOUNT_DUPLICATED");
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isEmpty();
	}

	@Test
	void 확인에_실패하면_앞의_유효한_기록은_그대로_둔다() throws Exception {
		member("mole@example.com");
		testUsers.createGoogleOnlyUser("taken@gmail.com");
		Cookie session = login("mole@example.com");
		verifyLink(session, EMAIL);
		Attempt attempt = google.startVerify(session, "LINK");

		google.callback(attempt, google.codeFor(attempt, googleSub("taken@gmail.com"), "taken@gmail.com"), session)
				.andExpect(status().isFound());

		Optional<GoogleVerification> record = verifications.find(sessionId(session), GoogleOAuthPurpose.LINK);
		assertThat(record).isPresent();
		assertThat(record.get().googleEmail()).isEqualTo(EMAIL);
	}

	// GET /api/v1/oauth/google/callback: WITHDRAW

	@Test
	void WITHDRAW_확인이_내_계정에_연결된_Google_계정과_같으면_탈퇴_화면으로_보내고_기록을_남긴다() throws Exception {
		testUsers.markOnboarded(testUsers.createGoogleOnlyUser(EMAIL));
		Cookie session = googleLogin();
		Attempt attempt = google.startVerify(session, "WITHDRAW");
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse response = google.callback(attempt, code, session)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/settings/leave");
		Optional<GoogleVerification> record = verifications.find(sessionId(session), GoogleOAuthPurpose.WITHDRAW);
		assertThat(record).isPresent();
		assertThat(record.get().googleSub()).isEqualTo(googleSub(EMAIL));
		// 목적이 다른 기록으로는 읽히지 않는다.
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isEmpty();
	}

	@Test
	void WITHDRAW인데_다른_Google_계정이면_GOOGLE_ACCOUNT_MISMATCH로_기록을_만들지_않는다() throws Exception {
		testUsers.markOnboarded(testUsers.createGoogleOnlyUser(EMAIL));
		Cookie session = googleLogin();
		Attempt attempt = google.startVerify(session, "WITHDRAW");
		String code = google.codeFor(attempt, googleSub("other@gmail.com"), "other@gmail.com");

		MockHttpServletResponse response = google.callback(attempt, code, session).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/settings/leave?error=GOOGLE_ACCOUNT_MISMATCH");
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.WITHDRAW)).isEmpty();
	}

	@Test
	void WITHDRAW인데_Google_계정이_연결되지_않은_계정이면_GOOGLE_ACCOUNT_MISMATCH다() throws Exception {
		member("mole@example.com");
		Cookie session = login("mole@example.com");
		Attempt attempt = google.startVerify(session, "WITHDRAW");
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse response = google.callback(attempt, code, session).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/settings/leave?error=GOOGLE_ACCOUNT_MISMATCH");
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.WITHDRAW)).isEmpty();
	}

	// GET /api/v1/oauth/google/callback: Google의 실패와 다른 세션

	@Test
	void Google에서_취소하면_시작한_화면으로_GOOGLE_VERIFICATION_FAILED를_붙여_보낸다() throws Exception {
		testUsers.markOnboarded(testUsers.createGoogleOnlyUser(EMAIL));
		Cookie session = googleLogin();
		fakeGoogle.clear();
		Attempt link = google.startVerify(session, "LINK");
		Attempt withdraw = google.startVerify(session, "WITHDRAW");

		MockHttpServletResponse linkResponse = google.callback(link.state(), null, "access_denied", link.cookie(), session)
				.andReturn().getResponse();
		MockHttpServletResponse withdrawResponse = google
				.callback(withdraw.state(), null, "access_denied", withdraw.cookie(), session).andReturn().getResponse();

		assertThat(location(linkResponse)).isEqualTo(LINK_FAILED);
		assertThat(location(withdrawResponse)).isEqualTo(WITHDRAW_FAILED);
		assertThat(fakeGoogle.tokenRequests()).isEmpty();
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isEmpty();
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.WITHDRAW)).isEmpty();
	}

	@Test
	void Google가_500이면_시작한_화면으로_GOOGLE_VERIFICATION_FAILED를_붙여_보낸다() throws Exception {
		member("mole@example.com");
		Cookie session = login("mole@example.com");
		Attempt attempt = google.startVerify(session, "LINK");
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);
		fakeGoogle.failTokenWith(500);

		MockHttpServletResponse response = google.callback(attempt, code, session).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LINK_FAILED);
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isEmpty();
	}

	@Test
	void 시작한_세션이_아닌_다른_세션에서_돌아오면_기록을_만들지_않는다() throws Exception {
		member("mole@example.com");
		Cookie started = login("mole@example.com");
		Cookie another = login("mole@example.com");
		Attempt attempt = google.startVerify(started, "LINK");
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse response = google.callback(attempt, code, another).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LINK_FAILED);
		assertThat(verifications.find(sessionId(started), GoogleOAuthPurpose.LINK)).isEmpty();
		assertThat(verifications.find(sessionId(another), GoogleOAuthPurpose.LINK)).isEmpty();
		assertThat(fakeGoogle.tokenRequests()).isEmpty();
	}

	@Test
	void 시작한_계정이_아닌_다른_계정의_세션에서_돌아오면_기록을_만들지_않는다() throws Exception {
		member("mole@example.com");
		member("other@example.com");
		Cookie started = login("mole@example.com");
		Cookie other = login("other@example.com");
		Attempt attempt = google.startVerify(started, "LINK");
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse response = google.callback(attempt, code, other).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LINK_FAILED);
		assertThat(verifications.find(sessionId(other), GoogleOAuthPurpose.LINK)).isEmpty();
	}

	@Test
	void 돌아왔을_때_로그인이_풀려_있으면_로그인_화면으로_보내고_기록을_만들지_않는다() throws Exception {
		member("mole@example.com");
		Cookie session = login("mole@example.com");
		Attempt attempt = google.startVerify(session, "LINK");
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse response = google.callback(attempt, code).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/login");
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isEmpty();
		assertThat(fakeGoogle.tokenRequests()).isEmpty();
	}

	@Test
	void 확인용_시도는_쿠키가_없으면_받아들이지_않는다() throws Exception {
		member("mole@example.com");
		Cookie session = login("mole@example.com");
		Attempt attempt = google.startVerify(session, "LINK");
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse response = google.callback(attempt.state(), code, null, null, session)
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/login?error=GOOGLE_LOGIN_FAILED");
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.LINK)).isEmpty();
	}

	/** 세션을 새로 만들거나 바꾸지 않았다. 세션 쿠키를 다시 내려주더라도 같은 세션이다(마지막 접속 시각을 늘린다). */
	private static void assertSessionKept(MockHttpServletResponse response, Cookie session) {
		String header = TestUsers.lastSessionCookieHeader(response);
		if (header != null) {
			assertThat(header).startsWith(session.getName() + "=" + session.getValue() + ";");
		}
	}

	/** 온보딩을 마친 이메일 계정. */
	private long member(String email) {
		long userId = testUsers.createEmailUser(email, PASSWORD);
		testUsers.markOnboarded(userId);
		return userId;
	}

	private Cookie login(String email) throws Exception {
		return sessionCookie(mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\" }"))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

	/** 가짜 Google로 로그인한 세션(Google로만 가입한 계정). */
	private Cookie googleLogin() throws Exception {
		return sessionCookie(google.loginWith(googleSub(EMAIL), EMAIL).andExpect(status().isFound()).andReturn()
				.getResponse());
	}

	/** 이 세션에서 LINK 확인을 마친다. */
	private void verifyLink(Cookie session, String googleEmail) throws Exception {
		Attempt attempt = google.startVerify(session, "LINK");
		MockHttpServletResponse response = google
				.callback(attempt, google.codeFor(attempt, googleSub(googleEmail), googleEmail), session)
				.andReturn().getResponse();
		assertThat(location(response)).isEqualTo(FRONTEND + "/settings/google?verified=true");
	}

}
