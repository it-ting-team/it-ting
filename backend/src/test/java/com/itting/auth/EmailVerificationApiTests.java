package com.itting.auth;

import static com.itting.support.SignupSteps.lastCookieHeader;
import static com.itting.support.SignupSteps.newEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.itting.support.FakeMattermost;
import com.itting.support.Mailpit;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.SignupSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /api/v1/email-verifications}와 {@code /check}. 메일은 테스트용 Mailpit이 받는다.
 */
@SpringBootTest(properties = "itting.generations=15,16,17,18,19,20,21")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class EmailVerificationApiTests {

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

	SignupSteps steps;

	Instant now;

	@BeforeEach
	void setUp() {
		now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		clock.setNow(now);
		steps = new SignupSteps(mockMvc, mailpit, fakeMattermost);
	}

	@Test
	void 코드를_보내면_201과_만료_시각을_주고_한글_메일이_간다() throws Exception {
		String email = newEmail();

		steps.sendEmailCode(email)
				.andExpect(status().isCreated())
				.andExpect(content().json("""
						{ "success": true, "data": { "expiresAt": "%s" }, "error": null }
						""".formatted(now.plus(Duration.ofMinutes(5))), JsonCompareMode.STRICT));

		Mailpit.Message mail = mailpit.latestTo(email);
		assertThat(mail.subject()).isEqualTo("[디굴디굴] 이메일 인증 코드");
		assertThat(mail.text()).contains("디굴디굴 가입 인증 코드는 " + mail.code() + "입니다.", "5분 안에 입력해 주세요.");
	}

	@Test
	void 재전송_간격_안에_다시_보내면_429이고_지난_뒤에는_새_코드만_맞다() throws Exception {
		String email = newEmail();
		steps.sendEmailCode(email).andExpect(status().isCreated());
		String first = mailpit.latestTo(email).code();

		clock.advance(Duration.ofSeconds(59));
		steps.sendEmailCode(email)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));

		clock.advance(Duration.ofSeconds(1));
		steps.sendEmailCode(email).andExpect(status().isCreated());
		assertThat(mailpit.countTo(email)).isEqualTo(2);
		String second = mailpit.latestTo(email).code();

		if (!first.equals(second)) {
			steps.checkEmailCode(email, first)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_MISMATCH"));
		}
		steps.checkEmailCode(email, second).andExpect(status().isOk());
	}

	@Test
	void 이메일_형식이_틀리면_400_INVALID_INPUT이다() throws Exception {
		steps.sendEmailCode("mole")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("email"));
	}

	@Test
	void 이미_가입한_이메일은_대소문자와_상관없이_409_EMAIL_DUPLICATED다() throws Exception {
		String email = newEmail();
		testUsers.createEmailUser(email, "mole1234!");

		steps.sendEmailCode(email.toUpperCase())
				.andExpect(status().isConflict())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": { "code": "EMAIL_DUPLICATED", "message": "이미 가입한 이메일입니다.", "fields": null, "detail": null }
						}
						""", JsonCompareMode.STRICT));
		assertThat(mailpit.countTo(email.toUpperCase())).isZero();
	}

	@Test
	void 맞는_코드면_200과_가입_쿠키를_주고_같은_코드는_다시_쓸_수_없다() throws Exception {
		String email = newEmail();
		steps.sendEmailCode(email).andExpect(status().isCreated());
		String code = mailpit.latestTo(email).code();

		MockHttpServletResponse response = steps.checkEmailCode(email, code)
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT))
				.andReturn().getResponse();
		assertThat(lastCookieHeader(response, SignupSteps.SIGNUP_COOKIE))
				.contains("Path=/api/v1", "Max-Age=3600", "Secure", "HttpOnly", "SameSite=Lax");

		steps.checkEmailCode(email, code)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_EXPIRED"));
	}

	@Test
	void 이메일은_대소문자를_가리지_않고_확인한다() throws Exception {
		String email = newEmail();
		steps.sendEmailCode(email.toUpperCase()).andExpect(status().isCreated());
		String code = mailpit.latestTo(email.toUpperCase()).code();

		steps.checkEmailCode(email, code).andExpect(status().isOk());
	}

	@Test
	void 네_번_틀려도_맞는_코드는_통과한다() throws Exception {
		String email = newEmail();
		steps.sendEmailCode(email).andExpect(status().isCreated());
		String code = mailpit.latestTo(email).code();

		for (int i = 0; i < 4; i++) {
			steps.checkEmailCode(email, wrong(code)).andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_MISMATCH"));
		}
		steps.checkEmailCode(email, code).andExpect(status().isOk());
	}

	@Test
	void 다섯_번_틀리면_코드를_지워_맞는_코드도_VERIFICATION_CODE_EXPIRED다() throws Exception {
		String email = newEmail();
		steps.sendEmailCode(email).andExpect(status().isCreated());
		String code = mailpit.latestTo(email).code();

		for (int i = 0; i < 5; i++) {
			steps.checkEmailCode(email, wrong(code))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_MISMATCH"));
		}
		steps.checkEmailCode(email, code)
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "VERIFICATION_CODE_EXPIRED",
						    "message": "인증 코드가 만료됐습니다. 코드를 다시 받아 주세요.",
						    "fields": null,
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
		// 코드를 지워도 재전송 간격은 그대로 센다.
		steps.sendEmailCode(email).andExpect(status().isTooManyRequests());
	}

	@Test
	void 보낸_적이_없거나_5분이_지난_코드는_VERIFICATION_CODE_EXPIRED다() throws Exception {
		steps.checkEmailCode(newEmail(), "123456")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_EXPIRED"));

		String email = newEmail();
		steps.sendEmailCode(email).andExpect(status().isCreated());
		String code = mailpit.latestTo(email).code();
		clock.advance(Duration.ofMinutes(5));
		steps.checkEmailCode(email, code)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_EXPIRED"));
	}

	@Test
	void 코드가_숫자_6자리가_아니면_400_INVALID_INPUT이다() throws Exception {
		steps.checkEmailCode(newEmail(), "12ab56")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("code"))
				.andExpect(jsonPath("$.error.fields[0].message").value("숫자 6자리여야 합니다."));
	}

	@Test
	void 같은_이메일로_동시에_보내도_하나만_보내고_나머지는_429다() throws Exception {
		String email = newEmail();

		List<Integer> statuses = SignupSteps.concurrently(5,
				() -> steps.sendEmailCode(email).andReturn().getResponse().getStatus());

		assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 429);
		assertThat(mailpit.countTo(email)).isEqualTo(1);
	}

	@Test
	void 틀린_확인이_동시에_와도_기록이_망가지지_않고_EXPIRED로_답한다() throws Exception {
		String email = newEmail();
		steps.sendEmailCode(email).andExpect(status().isCreated());
		String code = mailpit.latestTo(email).code();
		for (int i = 0; i < 3; i++) {
			steps.checkEmailCode(email, wrong(code)).andExpect(status().isBadRequest());
		}

		List<Integer> statuses = SignupSteps.concurrently(5,
				() -> steps.checkEmailCode(email, wrong(code)).andReturn().getResponse().getStatus());

		assertThat(statuses).containsOnly(400);
		steps.checkEmailCode(email, code)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VERIFICATION_CODE_EXPIRED"));
		clock.advance(Duration.ofSeconds(60));
		steps.sendEmailCode(email).andExpect(status().isCreated());
		steps.checkEmailCode(email, mailpit.latestTo(email).code()).andExpect(status().isOk());
	}

	/** 맞는 코드와 다른 숫자 6자리. */
	private static String wrong(String code) {
		return code.equals("000000") ? "111111" : "000000";
	}

}
