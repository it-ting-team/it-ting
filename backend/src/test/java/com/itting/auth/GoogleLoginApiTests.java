package com.itting.auth;

import static com.itting.support.GoogleSteps.OAUTH_COOKIE;
import static com.itting.support.GoogleSteps.hasCookie;
import static com.itting.support.GoogleSteps.location;
import static com.itting.support.GoogleSteps.query;
import static com.itting.support.GoogleSteps.withoutQuery;
import static com.itting.support.SignupSteps.lastCookieHeader;
import static com.itting.support.TestUsers.googleSub;
import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

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
 * {@code GET /api/v1/oauth/google}와 {@code GET /api/v1/oauth/google/callback}의 로그인 갈래.
 * Google은 {@link FakeGoogle}이 맡는다. 시작 주소를 부르고 {@code Location}의 {@code state} · {@code code_challenge}로 코드를
 * 발급해 콜백까지 이어서 부른다. 시간은 고정 시계로 돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class GoogleLoginApiTests {

	private static final String FRONTEND = "http://localhost:3000";

	private static final String EMAIL = "dawnwalk@gmail.com";

	private static final String LOGIN_FAILED = FRONTEND + "/login?error=GOOGLE_LOGIN_FAILED";

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

	GoogleSteps google;

	@BeforeEach
	void setUp() {
		clock.setNow(Instant.now().truncatedTo(ChronoUnit.SECONDS));
		fakeGoogle.clear();
		google = new GoogleSteps(mockMvc, fakeGoogle);
	}

	// GET /api/v1/oauth/google

	@Test
	void 시작하면_302로_Google_로그인_화면으로_보내고_state와_PKCE를_붙이고_쿠키를_준다() throws Exception {
		MockHttpServletResponse response = mockMvc.perform(get("/api/v1/oauth/google"))
				.andExpect(status().isFound())
				.andReturn().getResponse();

		String location = location(response);
		assertThat(withoutQuery(location)).isEqualTo(fakeGoogle.authorizationUri());
		Map<String, String> query = query(location);
		assertThat(query).containsEntry("response_type", "code")
				.containsEntry("client_id", FakeGoogle.CLIENT_ID)
				.containsEntry("redirect_uri", FakeGoogle.REDIRECT_URI)
				.containsEntry("scope", "openid email")
				.containsEntry("code_challenge_method", "S256");
		assertThat(query.get("state")).matches("[A-Za-z0-9_-]{43}");
		assertThat(query.get("code_challenge")).matches("[A-Za-z0-9_-]{43}");
		// 계정을 다시 고르게 하는 prompt는 붙이지 않는다(Google의 기본 동작을 따른다).
		assertThat(query).doesNotContainKey("prompt");
		// 응답이 본문 없이 이동만 시킨다. 세션도 가입 쿠키도 만들지 않는다.
		assertThat(response.getContentAsString()).isEmpty();
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
		assertThat(hasCookie(response, "ITTING_SIGNUP")).isFalse();

		// state와 같은 값을 HttpOnly 쿠키로도 준다. 서버는 시도를 10분 동안 기억한다.
		assertThat(lastCookieHeader(response, OAUTH_COOKIE))
				.startsWith(OAUTH_COOKIE + "=" + query.get("state") + ";")
				.contains("Max-Age=600", "Path=/api/v1/oauth/google", "Secure", "HttpOnly", "SameSite=Lax");
		assertThat(redis.getExpire("itting:oauth:google:" + query.get("state"))).isBetween(590L, 600L);
	}

	@Test
	void 시작할_때마다_다른_state와_code_challenge를_만든다() throws Exception {
		Attempt first = google.startLogin();
		Attempt second = google.startLogin();

		assertThat(first.state()).isNotEqualTo(second.state());
		assertThat(first.codeChallenge()).isNotEqualTo(second.codeChallenge());
	}

	@Test
	void 시작과_콜백은_GET만_로그인_없이_부를_수_있다() throws Exception {
		mockMvc.perform(post("/api/v1/oauth/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(post("/api/v1/oauth/google/callback"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/oauth/google/verify"))
				.andExpect(status().isUnauthorized());
	}

	// GET /api/v1/oauth/google/callback: 이미 가입한 계정

	@Test
	void 이미_가입한_계정은_세션_쿠키를_받고_프론트엔드_첫_화면으로_간다() throws Exception {
		long userId = testUsers.createGoogleOnlyUser(EMAIL);
		Attempt attempt = google.startLogin();
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse response = google.callback(attempt, code)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/");
		assertThat(response.getContentAsString()).isEmpty();
		assertThat(TestUsers.lastSessionCookieHeader(response))
				.contains("Max-Age=1209600", "Path=/", "Secure", "HttpOnly", "SameSite=Lax");
		// 가입 쿠키는 주지 않고, 시도 쿠키는 한 번 쓰고 지운다.
		assertThat(hasCookie(response, "ITTING_SIGNUP")).isFalse();
		assertThat(lastCookieHeader(response, OAUTH_COOKIE)).startsWith(OAUTH_COOKIE + "=;").contains("Max-Age=0");
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie(response)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId))
				.andExpect(jsonPath("$.data.nextStep").value("ONBOARDING"));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `user` WHERE google_sub = ?", Integer.class,
				googleSub(EMAIL))).isEqualTo(1);
	}

	@Test
	void Google에_보낸_요청은_인가_코드와_PKCE_검증값과_클라이언트_정보를_담는다() throws Exception {
		testUsers.createGoogleOnlyUser(EMAIL);
		Attempt attempt = google.startLogin();
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		google.callback(attempt, code).andExpect(status().isFound());

		assertThat(fakeGoogle.tokenRequests()).hasSize(1);
		FakeGoogle.TokenRequest request = fakeGoogle.tokenRequests().getFirst();
		assertThat(request.method()).isEqualTo("POST");
		assertThat(request.contentType()).startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
		assertThat(request.form()).containsEntry("grant_type", "authorization_code")
				.containsEntry("code", code)
				.containsEntry("redirect_uri", FakeGoogle.REDIRECT_URI)
				.containsEntry("client_id", FakeGoogle.CLIENT_ID)
				.containsEntry("client_secret", FakeGoogle.CLIENT_SECRET);
		// 시작할 때 보낸 code_challenge가 이 code_verifier의 S256이다.
		assertThat(FakeGoogle.challengeOf(request.form().get("code_verifier"))).isEqualTo(attempt.codeChallenge());
	}

	@Test
	void 이메일로_가입하고_Google을_연동한_계정도_Google로_로그인된다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", "mole1234!");
		testUsers.markOnboarded(userId);
		testUsers.linkGoogle(userId, EMAIL);

		MockHttpServletResponse response = google.loginWith(googleSub(EMAIL), EMAIL)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/");
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie(response)))
				.andExpect(jsonPath("$.data.userId").value(userId))
				.andExpect(jsonPath("$.data.nextStep").value("HOME"));
	}

	@Test
	void 로그인한_브라우저가_다시_Google로_로그인하면_이전_세션을_버리고_새_세션을_만든다() throws Exception {
		testUsers.createEmailUser("other@example.com", "mole1234!");
		Cookie previous = sessionCookie(mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"other@example.com\", \"password\": \"mole1234!\" }"))
				.andExpect(status().isCreated()).andReturn().getResponse());
		long userId = testUsers.createGoogleOnlyUser(EMAIL);

		MockHttpServletResponse response = google.loginWith(googleSub(EMAIL), EMAIL, previous)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		Cookie current = sessionCookie(response);
		assertThat(current.getValue()).isNotEqualTo(previous.getValue());
		mockMvc.perform(get("/api/v1/me").cookie(current)).andExpect(jsonPath("$.data.userId").value(userId));
		mockMvc.perform(get("/api/v1/me").cookie(previous))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 탈퇴한_계정의_Google_식별자는_비어_있어_처음_쓰는_계정이_된다() throws Exception {
		long userId = testUsers.createGoogleOnlyUser(EMAIL);
		jdbcTemplate.update("UPDATE `user` SET status = 'WITHDRAWN', google_sub = NULL, google_email = NULL WHERE id = ?",
				userId);

		MockHttpServletResponse response = google.loginWith(googleSub(EMAIL), EMAIL)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/signup/ssafy?method=google");
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
	}

	// GET /api/v1/oauth/google/callback: 처음 쓰는 Google 계정

	@Test
	void 처음_쓰는_Google_계정은_계정을_만들지_않고_가입_쿠키를_주고_회원가입_3으로_보낸다() throws Exception {
		int usersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class);

		MockHttpServletResponse response = google.loginWith("google-sub-new", "newbie@gmail.com")
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/signup/ssafy?method=google");
		assertThat(lastCookieHeader(response, "ITTING_SIGNUP"))
				.contains("Max-Age=3600", "Path=/api/v1", "Secure", "HttpOnly", "SameSite=Lax");
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class)).isEqualTo(usersBefore);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `user` WHERE google_sub = ?", Integer.class,
				"google-sub-new")).isZero();
	}

	// GET /api/v1/oauth/google/callback: 정지된 계정

	@Test
	void 정지된_계정은_세션을_만들지_않고_사유와_끝나는_시각을_붙여_로그인_화면으로_보낸다() throws Exception {
		long userId = testUsers.createGoogleOnlyUser(EMAIL);
		long adminId = testUsers.createAdmin();
		Instant now = clock.instant();
		Instant earlier = now.plus(Duration.ofDays(1));
		Instant later = now.plus(Duration.ofDays(2));
		String reason = "욕설 & 비방=반복+3회";
		testUsers.suspend(userId, adminId, "먼저 끝나는 정지", now.minus(Duration.ofDays(1)), earlier, null);
		testUsers.suspend(userId, adminId, reason, now.minus(Duration.ofHours(1)), later, null);

		MockHttpServletResponse response = google.loginWith(googleSub(EMAIL), EMAIL)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		String location = location(response);
		assertThat(withoutQuery(location)).isEqualTo(FRONTEND + "/login");
		// 이메일 로그인의 error.detail과 같은 이름이다. 정지 기록이 겹치면 가장 늦게 끝나는 것이다. 특수문자는 그대로 되돌아온다.
		assertThat(query(location)).containsEntry("error", "ACCOUNT_SUSPENDED")
				.containsEntry("reason", reason)
				.containsEntry("endsAt", later.toString());
		assertThat(location).contains("%26", "%3D", "%2B");
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
		assertThat(hasCookie(response, "ITTING_SIGNUP")).isFalse();
	}

	@Test
	void 정지가_끝났거나_해제됐거나_아직_시작하지_않았으면_로그인된다() throws Exception {
		long userId = testUsers.createGoogleOnlyUser(EMAIL);
		long adminId = testUsers.createAdmin();
		Instant now = clock.instant();
		testUsers.suspend(userId, adminId, "끝난 정지", now.minus(Duration.ofDays(3)), now.minus(Duration.ofDays(1)), null);
		testUsers.suspend(userId, adminId, "해제한 정지", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)),
				now.minus(Duration.ofHours(1)));
		testUsers.suspend(userId, adminId, "아직 시작하지 않은 정지", now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(2)),
				null);

		MockHttpServletResponse response = google.loginWith(googleSub(EMAIL), EMAIL)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/");
		assertThat(hasCookie(response, "ITTING_SESSION")).isTrue();
	}

	// GET /api/v1/oauth/google/callback: state

	@Test
	void 시도_쿠키가_없으면_로그인하지_못하고_세션을_만들지_않는다() throws Exception {
		testUsers.createGoogleOnlyUser(EMAIL);
		Attempt attempt = google.startLogin();
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		// 남이 시작한 주소를 내 브라우저가 열었다(내 브라우저에는 그 시도의 쿠키가 없다).
		MockHttpServletResponse response = google.callback(attempt.state(), code, null, null)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LOGIN_FAILED);
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
		assertThat(fakeGoogle.tokenRequests()).isEmpty();
	}

	@Test
	void 시도_쿠키와_state가_다르면_로그인하지_못한다() throws Exception {
		testUsers.createGoogleOnlyUser(EMAIL);
		Attempt mine = google.startLogin();
		Attempt theirs = google.startLogin();
		String code = google.codeFor(theirs, googleSub(EMAIL), EMAIL);

		// 내 브라우저의 쿠키(내 시도)로 남의 시도의 state와 코드를 받았다.
		MockHttpServletResponse response = google.callback(theirs.state(), code, null, mine.cookie())
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LOGIN_FAILED);
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
		assertThat(fakeGoogle.tokenRequests()).isEmpty();
		// 남의 시도를 지우지도 않는다. 남은 그대로 마칠 수 있다.
		google.callback(theirs, code).andExpect(status().isFound());
		assertThat(fakeGoogle.tokenRequests()).hasSize(1);
	}

	@Test
	void state가_없거나_서버가_만든_모양이_아니면_로그인하지_못한다() throws Exception {
		Attempt attempt = google.startLogin();
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		assertThat(location(google.callback((String) null, code, null, attempt.cookie()).andReturn().getResponse()))
				.isEqualTo(LOGIN_FAILED);
		assertThat(location(google.callback("short", code, null, new Cookie(OAUTH_COOKIE, "short")).andReturn()
				.getResponse())).isEqualTo(LOGIN_FAILED);
		// 서버가 만든 적 없는 state는 쿠키가 같아도 받지 않는다.
		String forged = "A".repeat(43);
		assertThat(location(google.callback(forged, code, null, new Cookie(OAUTH_COOKIE, forged)).andReturn()
				.getResponse())).isEqualTo(LOGIN_FAILED);
		assertThat(fakeGoogle.tokenRequests()).isEmpty();
	}

	@Test
	void 같은_시도로_두_번_돌아오면_두_번째는_로그인하지_못한다() throws Exception {
		testUsers.createGoogleOnlyUser(EMAIL);
		Attempt attempt = google.startLogin();
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		MockHttpServletResponse first = google.callback(attempt, code).andReturn().getResponse();
		MockHttpServletResponse second = google.callback(attempt, google.codeFor(attempt, googleSub(EMAIL), EMAIL))
				.andReturn().getResponse();

		assertThat(location(first)).isEqualTo(FRONTEND + "/");
		assertThat(location(second)).isEqualTo(LOGIN_FAILED);
		assertThat(hasCookie(second, "ITTING_SESSION")).isFalse();
		assertThat(fakeGoogle.tokenRequests()).hasSize(1);
	}

	@Test
	void 시작한_지_10분이_지나면_로그인하지_못한다() throws Exception {
		testUsers.createGoogleOnlyUser(EMAIL);
		Attempt attempt = google.startLogin();
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		clock.advance(Duration.ofMinutes(10));
		MockHttpServletResponse response = google.callback(attempt, code).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LOGIN_FAILED);
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
		assertThat(fakeGoogle.tokenRequests()).isEmpty();
	}

	@Test
	void 시작한_지_10분이_되기_전에는_로그인된다() throws Exception {
		testUsers.createGoogleOnlyUser(EMAIL);
		Attempt attempt = google.startLogin();
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);

		clock.advance(Duration.ofMinutes(10).minusSeconds(1));
		MockHttpServletResponse response = google.callback(attempt, code).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(FRONTEND + "/");
	}

	// GET /api/v1/oauth/google/callback: Google의 실패

	@Test
	void Google에서_취소하면_토큰을_받으러_가지_않고_로그인_실패다() throws Exception {
		testUsers.createGoogleOnlyUser(EMAIL);
		Attempt attempt = google.startLogin();

		MockHttpServletResponse response = google.callback(attempt.state(), null, "access_denied", attempt.cookie())
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LOGIN_FAILED);
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
		assertThat(fakeGoogle.tokenRequests()).isEmpty();
		// 취소한 시도도 쓴 것으로 친다.
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);
		assertThat(location(google.callback(attempt, code).andReturn().getResponse())).isEqualTo(LOGIN_FAILED);
	}

	@Test
	void 코드가_없으면_로그인_실패다() throws Exception {
		Attempt attempt = google.startLogin();

		MockHttpServletResponse response = google.callback(attempt.state(), null, null, attempt.cookie())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LOGIN_FAILED);
		assertThat(fakeGoogle.tokenRequests()).isEmpty();
	}

	@Test
	void Google의_토큰_주소가_500이면_로그인_실패다() throws Exception {
		testUsers.createGoogleOnlyUser(EMAIL);
		Attempt attempt = google.startLogin();
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);
		fakeGoogle.failTokenWith(500);

		MockHttpServletResponse response = google.callback(attempt, code)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LOGIN_FAILED);
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
		assertThat(hasCookie(response, "ITTING_SIGNUP")).isFalse();
		assertThat(fakeGoogle.tokenRequests()).hasSize(1);
	}

	@Test
	void Google의_사용자_정보_주소가_500이면_로그인_실패다() throws Exception {
		testUsers.createGoogleOnlyUser(EMAIL);
		Attempt attempt = google.startLogin();
		String code = google.codeFor(attempt, googleSub(EMAIL), EMAIL);
		fakeGoogle.failUserinfoWith(500);

		MockHttpServletResponse response = google.callback(attempt, code).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LOGIN_FAILED);
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
	}

	@Test
	void Google가_거절한_코드나_맞지_않는_PKCE는_로그인_실패다() throws Exception {
		testUsers.createGoogleOnlyUser(EMAIL);
		Attempt unknownCode = google.startLogin();
		Attempt wrongChallenge = google.startLogin();

		// 발급한 적 없는 코드(invalid_grant)
		assertThat(location(google.callback(unknownCode, "never-issued").andReturn().getResponse()))
				.isEqualTo(LOGIN_FAILED);
		// 시작할 때와 다른 code_challenge로 발급된 코드(서버가 보낸 code_verifier가 맞지 않음)
		String code = fakeGoogle.issueCode(googleSub(EMAIL), EMAIL, FakeGoogle.challengeOf("some-other-verifier"));
		MockHttpServletResponse response = google.callback(wrongChallenge, code).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LOGIN_FAILED);
		assertThat(hasCookie(response, "ITTING_SESSION")).isFalse();
	}

	@Test
	void Google가_이메일을_주지_않으면_로그인_실패다() throws Exception {
		Attempt attempt = google.startLogin();
		String code = google.codeFor(attempt, "google-sub-no-email", null);

		MockHttpServletResponse response = google.callback(attempt, code).andReturn().getResponse();

		assertThat(location(response)).isEqualTo(LOGIN_FAILED);
		assertThat(hasCookie(response, "ITTING_SIGNUP")).isFalse();
	}

}
