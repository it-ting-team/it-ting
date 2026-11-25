package com.itting.auth;

import static com.itting.support.GoogleSteps.hasCookie;
import static com.itting.support.GoogleSteps.location;
import static com.itting.support.SignupSteps.lastCookieHeader;
import static com.itting.support.SignupSteps.newEmail;
import static com.itting.support.SignupSteps.newMattermostId;
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
import com.itting.support.FakeMattermost;
import com.itting.support.GoogleSteps;
import com.itting.support.Mailpit;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.SignupSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /api/v1/users}의 Google 가입 갈래. Google 콜백이 가입 진행 기록을 만든 뒤 SSAFY 인증을 거쳐 계정을 만든다.
 * 이메일 가입은 {@code SignupApiTests}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class GoogleSignupApiTests {

	private static final String SUB = "google-sub-newbie";

	private static final String EMAIL = "newbie@gmail.com";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	FakeGoogle fakeGoogle;

	@Autowired
	Mailpit mailpit;

	@Autowired
	FakeMattermost fakeMattermost;

	@Autowired
	MutableClock clock;

	@Autowired
	TestUsers testUsers;

	@Autowired
	JdbcTemplate jdbcTemplate;

	GoogleSteps google;

	SignupSteps signup;

	long floorId;

	@BeforeEach
	void setUp() {
		clock.setNow(Instant.now().truncatedTo(ChronoUnit.SECONDS));
		fakeGoogle.clear();
		google = new GoogleSteps(mockMvc, fakeGoogle);
		signup = new SignupSteps(mockMvc, mailpit, fakeMattermost);
		floorId = testUsers.seoulFloorId(3);
	}

	@Test
	void Google로_가입하면_Google_식별자를_채우고_이메일과_비밀번호는_비운_채_계정을_만들고_로그인시킨다() throws Exception {
		Cookie signupCookie = googleSignupCookie(SUB, EMAIL);
		String mattermostId = newMattermostId();
		signup.sendSsafyCode(signupCookie, 15, mattermostId).andExpect(status().isCreated());

		MockHttpServletResponse response = signup.signUp(signupCookie, null, signup.lastSsafyCode(mattermostId), floorId)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.nextStep").value("ONBOARDING"))
				.andReturn().getResponse();

		long userId = ((Number) JsonPath.read(response.getContentAsString(), "$.data.userId")).longValue();
		Map<String, Object> user = jdbcTemplate.queryForMap("SELECT * FROM `user` WHERE id = ?", userId);
		assertThat(user.get("google_sub")).isEqualTo(SUB);
		assertThat(user.get("google_email")).isEqualTo(EMAIL);
		assertThat(user.get("email")).isNull();
		assertThat(user.get("password_hash")).isNull();
		assertThat(user.get("mattermost_id")).isEqualTo(mattermostId);
		assertThat(user.get("role")).isEqualTo("USER");
		assertThat(user.get("status")).isEqualTo("ACTIVE");
		assertThat(user.get("ssafy_verified_at")).isNotNull();
		assertThat(user.get("terms_agreed_at")).isNotNull();
		assertThat(user.get("onboarded_at")).isNull();
		Map<String, Object> profile = jdbcTemplate.queryForMap("SELECT * FROM user_profile WHERE user_id = ?", userId);
		assertThat(((Number) profile.get("generation")).intValue()).isEqualTo(15);
		assertThat(((Number) profile.get("campus_floor_id")).longValue()).isEqualTo(floorId);

		// 가입 쿠키는 지우고 세션 쿠키를 준다. 그 세션으로 로그인한 상태다.
		assertThat(lastCookieHeader(response, SignupSteps.SIGNUP_COOKIE)).contains("Max-Age=0");
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie(response)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId))
				.andExpect(jsonPath("$.data.nextStep").value("ONBOARDING"));
		// 가입 진행 기록도 지웠다. 같은 가입 쿠키로 다시 만들 수 없다.
		signup.signUp(signupCookie, null, signup.lastSsafyCode(mattermostId), floorId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SIGNUP_EXPIRED"));
	}

	@Test
	void 가입을_마친_Google_계정은_다음부터_Google로_로그인되고_이메일_로그인은_못_한다() throws Exception {
		Cookie signupCookie = googleSignupCookie(SUB, EMAIL);
		String mattermostId = newMattermostId();
		signup.sendSsafyCode(signupCookie, 15, mattermostId).andExpect(status().isCreated());
		MockHttpServletResponse created = signup.signUp(signupCookie, null, signup.lastSsafyCode(mattermostId), floorId)
				.andExpect(status().isCreated())
				.andReturn().getResponse();
		long userId = ((Number) JsonPath.read(created.getContentAsString(), "$.data.userId")).longValue();

		MockHttpServletResponse again = google.loginWith(SUB, EMAIL).andExpect(status().isFound()).andReturn().getResponse();

		assertThat(location(again)).isEqualTo("http://localhost:3000/auth/google");
		mockMvc.perform(get("/api/v1/me").cookie(sessionCookie(again)))
				.andExpect(jsonPath("$.data.userId").value(userId));
		mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
						.content("{ \"email\": \"" + EMAIL + "\", \"password\": \"mole1234!\" }"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
	}

	@Test
	void Google_가입은_비밀번호를_보내도_저장하지_않고_규칙에_어긋나면_400이다() throws Exception {
		Cookie signupCookie = googleSignupCookie(SUB, EMAIL);
		String mattermostId = newMattermostId();
		signup.sendSsafyCode(signupCookie, 15, mattermostId).andExpect(status().isCreated());
		String code = signup.lastSsafyCode(mattermostId);

		signup.signUp(signupCookie, "mole1234", code, floorId)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("password"));
		MockHttpServletResponse response = signup.signUp(signupCookie, "mole1234!", code, floorId)
				.andExpect(status().isCreated())
				.andReturn().getResponse();

		long userId = ((Number) JsonPath.read(response.getContentAsString(), "$.data.userId")).longValue();
		assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM `user` WHERE id = ?", String.class, userId))
				.isNull();
	}

	@Test
	void SSAFY_코드를_확인하기_전에는_계정이_없다() throws Exception {
		Cookie signupCookie = googleSignupCookie(SUB, EMAIL);

		signup.signUp(signupCookie, null, "123456", floorId)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_EXPIRED"));

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `user` WHERE google_sub = ?", Integer.class, SUB))
				.isZero();
	}

	@Test
	void 그_사이_같은_Google_계정으로_가입했으면_409_GOOGLE_ACCOUNT_DUPLICATED다() throws Exception {
		Cookie signupCookie = googleSignupCookie(SUB, EMAIL);
		String mattermostId = newMattermostId();
		signup.sendSsafyCode(signupCookie, 15, mattermostId).andExpect(status().isCreated());
		// 다른 브라우저에서 같은 Google 계정으로 먼저 가입을 마쳤다.
		long other = testUsers.createGoogleOnlyUser("newbie@gmail.com");
		jdbcTemplate.update("UPDATE `user` SET google_sub = ? WHERE id = ?", SUB, other);

		signup.signUp(signupCookie, null, signup.lastSsafyCode(mattermostId), floorId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("GOOGLE_ACCOUNT_DUPLICATED"));
	}

	@Test
	void 그_사이_같은_Mattermost_ID로_가입했으면_409_MATTERMOST_ID_DUPLICATED다() throws Exception {
		Cookie signupCookie = googleSignupCookie(SUB, EMAIL);
		String mattermostId = newMattermostId();
		signup.sendSsafyCode(signupCookie, 15, mattermostId).andExpect(status().isCreated());
		long other = testUsers.createEmailUser(newEmail(), "other1234!");
		testUsers.setMattermostId(other, mattermostId);

		signup.signUp(signupCookie, null, signup.lastSsafyCode(mattermostId), floorId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MATTERMOST_ID_DUPLICATED"));
	}

	@Test
	void 가입_진행_기록은_1시간_뒤_사라진다() throws Exception {
		Cookie signupCookie = googleSignupCookie(SUB, EMAIL);
		String mattermostId = newMattermostId();
		signup.sendSsafyCode(signupCookie, 15, mattermostId).andExpect(status().isCreated());
		String code = signup.lastSsafyCode(mattermostId);

		clock.advance(Duration.ofHours(1));

		signup.signUp(signupCookie, null, code, floorId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SIGNUP_EXPIRED"));
	}

	@Test
	void Google_콜백이_이_브라우저의_이전_가입_진행_기록을_덮어쓴다() throws Exception {
		Cookie emailSignup = signup.verifyEmail(newEmail());
		signup.sendSsafyCode(emailSignup, 15, newMattermostId()).andExpect(status().isCreated());

		MockHttpServletResponse response = google.loginWith(SUB, EMAIL, emailSignup)
				.andExpect(status().isFound())
				.andReturn().getResponse();

		assertThat(location(response)).isEqualTo("http://localhost:3000/signup/ssafy?via=google");
		// 이전 이메일 가입 기록은 지워져 그 쿠키로는 더 진행할 수 없다.
		signup.sendSsafyCode(emailSignup, 15, newMattermostId())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SIGNUP_EXPIRED"));
		// 새 Google 가입 기록으로는 진행할 수 있다.
		signup.sendSsafyCode(SignupSteps.signupCookie(response), 15, newMattermostId()).andExpect(status().isCreated());
	}

	@Test
	void 가입_진행_중에_Google_로그인을_다시_시작하면_새_기록이_이전_기록을_덮어쓴다() throws Exception {
		MockHttpServletResponse first = google.loginWith(SUB, EMAIL).andReturn().getResponse();
		Cookie firstCookie = SignupSteps.signupCookie(first);

		MockHttpServletResponse second = google.loginWith("google-sub-other", "other@gmail.com", firstCookie)
				.andReturn().getResponse();

		assertThat(hasCookie(second, SignupSteps.SIGNUP_COOKIE)).isTrue();
		signup.sendSsafyCode(firstCookie, 15, newMattermostId())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SIGNUP_EXPIRED"));
		signup.sendSsafyCode(SignupSteps.signupCookie(second), 15, newMattermostId()).andExpect(status().isCreated());
	}

	@Test
	void 가입_진행_중_Google로_가입한_계정은_다른_Google_계정과_섞이지_않는다() throws Exception {
		// 이미 가입한 계정이 있어도 처음 쓰는 Google 계정의 가입에는 영향이 없다.
		testUsers.createGoogleOnlyUser("existing@gmail.com");
		Cookie signupCookie = googleSignupCookie(SUB, EMAIL);
		String mattermostId = newMattermostId();
		signup.sendSsafyCode(signupCookie, 15, mattermostId).andExpect(status().isCreated());

		signup.signUp(signupCookie, null, signup.lastSsafyCode(mattermostId), floorId)
				.andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForObject("SELECT google_email FROM `user` WHERE google_sub = ?", String.class, SUB))
				.isEqualTo(EMAIL);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `user` WHERE google_sub = ?", Integer.class,
				googleSub("existing@gmail.com"))).isEqualTo(1);
	}

	/** Google 콜백이 만든 가입 쿠키. 처음 쓰는 Google 계정이 돌아온 것이다. */
	private Cookie googleSignupCookie(String sub, String email) throws Exception {
		MockHttpServletResponse response = google.loginWith(sub, email)
				.andExpect(status().isFound())
				.andReturn().getResponse();
		assertThat(location(response)).isEqualTo("http://localhost:3000/signup/ssafy?via=google");
		return SignupSteps.signupCookie(response);
	}

}
