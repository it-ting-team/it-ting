package com.itting.auth;

import static com.itting.support.SignupSteps.lastCookieHeader;
import static com.itting.support.SignupSteps.newEmail;
import static com.itting.support.SignupSteps.newMattermostId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.itting.support.FakeMattermost;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /api/v1/users}. 이메일 인증 → SSAFY 인증 → 계정 만들기를 실제 API로 진행한다.
 */
@SpringBootTest(properties = "itting.generations=15,16,17,18,19,20,21")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class SignupApiTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	MockMvc mockMvc;

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

	@Autowired
	PasswordEncoder passwordEncoder;

	SignupSteps steps;

	long floorId;

	@BeforeEach
	void setUp() {
		clock.setNow(Instant.now().truncatedTo(ChronoUnit.SECONDS));
		steps = new SignupSteps(mockMvc, mailpit, fakeMattermost);
		floorId = testUsers.seoulFloorId(3);
	}

	@Test
	void 계정과_프로필을_만들고_201과_ONBOARDING을_주며_로그인시킨다() throws Exception {
		String email = newEmail();
		String id = newMattermostId();
		Cookie signup = steps.verifyEmail(email);
		steps.sendSsafyCode(signup, 15, id).andExpect(status().isCreated());

		MockHttpServletResponse response = steps.signUp(signup, PASSWORD, steps.lastSsafyCode(id), floorId)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.nextStep").value("ONBOARDING"))
				.andExpect(jsonPath("$.error").doesNotExist())
				.andReturn().getResponse();

		long userId = ((Number) com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.data.userId")).longValue();
		Map<String, Object> user = jdbcTemplate.queryForMap("SELECT * FROM `user` WHERE id = ?", userId);
		assertThat(user.get("email")).isEqualTo(email);
		assertThat(passwordEncoder.matches(PASSWORD, (String) user.get("password_hash"))).isTrue();
		assertThat(user.get("mattermost_id")).isEqualTo(id);
		assertThat(user.get("role")).isEqualTo("USER");
		assertThat(user.get("status")).isEqualTo("ACTIVE");
		assertThat(user.get("ssafy_verified_at")).isNotNull();
		assertThat(user.get("terms_agreed_at")).isNotNull();
		assertThat(user.get("onboarded_at")).isNull();
		assertThat(user.get("google_sub")).isNull();
		Map<String, Object> profile = jdbcTemplate.queryForMap("SELECT * FROM user_profile WHERE user_id = ?", userId);
		assertThat(((Number) profile.get("generation")).intValue()).isEqualTo(15);
		assertThat(((Number) profile.get("campus_floor_id")).longValue()).isEqualTo(floorId);
		assertThat(profile.get("nickname")).isNull();

		assertThat(lastCookieHeader(response, SignupSteps.SIGNUP_COOKIE)).contains("Max-Age=0");
		mockMvc.perform(get("/api/v1/me").cookie(TestUsers.sessionCookie(response)))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "data": { "userId": %d, "nextStep": "ONBOARDING", "campusName": "서울", "generation": 15, "floor": 3 } }
						""".formatted(userId), JsonCompareMode.LENIENT));
		mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content("""
				{ "email": "%s", "password": "%s" }
				""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated());

		// 가입 진행 기록은 지웠다. 같은 가입 쿠키로 다시 만들 수 없다.
		steps.signUp(signup, PASSWORD, steps.lastSsafyCode(id), floorId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SIGNUP_EXPIRED"));
	}

	@Test
	void 가입_쿠키가_없으면_409_SIGNUP_EXPIRED다() throws Exception {
		steps.signUp(null, PASSWORD, "123456", floorId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("SIGNUP_EXPIRED"));
	}

	@Test
	void SSAFY_코드를_보낸_적이_없거나_5분이_지났으면_VERIFICATION_CODE_EXPIRED다() throws Exception {
		Cookie signup = steps.verifyEmail(newEmail());
		steps.signUp(signup, PASSWORD, "123456", floorId)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_EXPIRED"));

		String id = newMattermostId();
		steps.sendSsafyCode(signup, 15, id).andExpect(status().isCreated());
		clock.advance(Duration.ofMinutes(5));
		steps.signUp(signup, PASSWORD, steps.lastSsafyCode(id), floorId)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_EXPIRED"));
	}

	@Test
	void SSAFY_코드가_틀리면_MISMATCH이고_다섯_번_틀리면_맞는_코드도_EXPIRED다() throws Exception {
		Cookie signup = steps.verifyEmail(newEmail());
		String id = newMattermostId();
		steps.sendSsafyCode(signup, 15, id).andExpect(status().isCreated());
		String code = steps.lastSsafyCode(id);
		String wrong = code.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 5; i++) {
			steps.signUp(signup, PASSWORD, wrong, floorId)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_MISMATCH"));
		}
		steps.signUp(signup, PASSWORD, code, floorId)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_EXPIRED"));
	}

	@Test
	void 비밀번호가_규칙에_맞지_않으면_400과_어긋난_규칙을_준다() throws Exception {
		Cookie signup = signupWithSsafyCode();

		steps.signUp(signup, "mole1234", "123456", floorId)
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "INVALID_INPUT",
						    "message": "입력값이 올바르지 않습니다.",
						    "fields": [ { "field": "password", "message": "특수기호를 1개 이상 넣어야 합니다." } ],
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
		steps.signUp(signup, "mole12!", "123456", floorId)
				.andExpect(jsonPath("$.error.fields[0].message").value("8자 이상 20자 이하여야 합니다."));
		steps.signUp(signup, "mole1234!mole1234!abc", "123456", floorId)
				.andExpect(jsonPath("$.error.fields[0].message").value("8자 이상 20자 이하여야 합니다."));
		steps.signUp(signup, "mole 1234!", "123456", floorId)
				.andExpect(jsonPath("$.error.fields[0].message").value("영문, 숫자, 특수기호 !@#$%만 쓸 수 있습니다."));
		steps.signUp(signup, "12345678!", "123456", floorId)
				.andExpect(jsonPath("$.error.fields[0].message").value("영문을 1개 이상 넣어야 합니다."));
	}

	@Test
	void 이메일_가입인데_비밀번호가_없으면_400이다() throws Exception {
		Cookie signup = signupWithSsafyCode();

		steps.signUp(signup, null, "123456", floorId)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("password"))
				.andExpect(jsonPath("$.error.fields[0].message").value("필수 값입니다."));
	}

	@Test
	void 동의하지_않으면_400이다() throws Exception {
		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
				{ "password": "mole1234!", "code": "123456", "campusFloorId": 1, "hasAgreedTerms": false, "hasAgreedPrivacy": true }
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("hasAgreedTerms"))
				.andExpect(jsonPath("$.error.fields[0].message").value("동의해야 합니다."));
	}

	@Test
	void 없거나_지운_캠퍼스_층이면_404_CAMPUS_FLOOR_NOT_FOUND다() throws Exception {
		String id = newMattermostId();
		Cookie signup = steps.verifyEmail(newEmail());
		steps.sendSsafyCode(signup, 15, id).andExpect(status().isCreated());
		String code = steps.lastSsafyCode(id);

		steps.signUp(signup, PASSWORD, code, 999_999)
				.andExpect(status().isNotFound())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": { "code": "CAMPUS_FLOOR_NOT_FOUND", "message": "없는 캠퍼스-층입니다.", "fields": null, "detail": null }
						}
						""", JsonCompareMode.STRICT));
		jdbcTemplate.update("UPDATE campus_floor SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", floorId);
		steps.signUp(signup, PASSWORD, code, floorId)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CAMPUS_FLOOR_NOT_FOUND"));
	}

	@Test
	void 그_사이_같은_이메일로_가입했으면_409_EMAIL_DUPLICATED다() throws Exception {
		String email = newEmail();
		String id = newMattermostId();
		Cookie signup = steps.verifyEmail(email);
		steps.sendSsafyCode(signup, 15, id).andExpect(status().isCreated());
		testUsers.createEmailUser(email, "other1234!");

		steps.signUp(signup, PASSWORD, steps.lastSsafyCode(id), floorId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));
	}

	@Test
	void 그_사이_같은_Mattermost_ID로_가입했으면_409_MATTERMOST_ID_DUPLICATED다() throws Exception {
		String id = newMattermostId();
		Cookie signup = steps.verifyEmail(newEmail());
		steps.sendSsafyCode(signup, 15, id).andExpect(status().isCreated());
		long other = testUsers.createEmailUser(newEmail(), "other1234!");
		testUsers.setMattermostId(other, id);

		steps.signUp(signup, PASSWORD, steps.lastSsafyCode(id), floorId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MATTERMOST_ID_DUPLICATED"));
	}

	/** 이메일 인증과 SSAFY 코드 보내기까지 마친 가입 쿠키. */
	private Cookie signupWithSsafyCode() throws Exception {
		Cookie signup = steps.verifyEmail(newEmail());
		steps.sendSsafyCode(signup, 15, newMattermostId()).andExpect(status().isCreated());
		return signup;
	}

}
