package com.itting.auth;

import static com.itting.support.SignupSteps.newEmail;
import static com.itting.support.SignupSteps.newMattermostId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /api/v1/ssafy-verifications}. Mattermost 웹후크는 {@link FakeMattermost}가 받는다.
 * 기수마다 웹후크의 답이 다르다({@link TestcontainersConfiguration#testServiceProperties}).
 */
@SpringBootTest(properties = "itting.generations=15,16,17,18,19,20,21")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class SsafyVerificationApiTests {

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
		fakeMattermost.clear();
		steps = new SignupSteps(mockMvc, mailpit, fakeMattermost);
	}

	@Test
	void 고른_기수의_웹후크로_그_아이디에게_한글_DM을_보내고_201과_만료_시각을_준다() throws Exception {
		Cookie signup = steps.verifyEmail(newEmail());
		String id = newMattermostId();

		steps.sendSsafyCode(signup, 16, id)
				.andExpect(status().isCreated())
				.andExpect(content().json("""
						{ "success": true, "data": { "expiresAt": "%s" }, "error": null }
						""".formatted(now.plus(Duration.ofMinutes(5))), JsonCompareMode.STRICT));

		assertThat(fakeMattermost.received()).singleElement().satisfies(message -> {
			assertThat(message.hook()).isEqualTo("ok-16");
			assertThat(message.contentType()).startsWith("application/json");
			assertThat(message.body())
					.contains("\"channel\":\"@" + id + "\"")
					.contains("[디굴디굴] SSAFY 인증 코드는 " + steps.lastSsafyCode(id) + "입니다. 5분 안에 입력해 주세요.");
		});
	}

	@Test
	void 아이디는_앞뒤_공백과_앞의_골뱅이를_떼고_소문자로_보낸다() throws Exception {
		Cookie signup = steps.verifyEmail(newEmail());
		String id = newMattermostId();

		steps.sendSsafyCode(signup, 16, " @" + id.toUpperCase() + " ").andExpect(status().isCreated());

		assertThat(fakeMattermost.received()).singleElement()
				.satisfies(message -> assertThat(message.body()).contains("\"@" + id + "\""));
	}

	@Test
	void 기수의_웹후크를_돌아가며_쓴다() throws Exception {
		steps.sendSsafyCode(steps.verifyEmail(newEmail()), 15, newMattermostId()).andExpect(status().isCreated());
		steps.sendSsafyCode(steps.verifyEmail(newEmail()), 15, newMattermostId()).andExpect(status().isCreated());

		assertThat(fakeMattermost.receivedBy("ok-15a")).hasSize(1);
		assertThat(fakeMattermost.receivedBy("ok-15b")).hasSize(1);
	}

	@Test
	void 웹후크가_500이면_다음_웹후크로_보낸다() throws Exception {
		steps.sendSsafyCode(steps.verifyEmail(newEmail()), 17, newMattermostId()).andExpect(status().isCreated());
		steps.sendSsafyCode(steps.verifyEmail(newEmail()), 17, newMattermostId()).andExpect(status().isCreated());

		// 두 번 가운데 한 번은 500인 웹후크부터 시작한다. 두 번 모두 성공한 웹후크가 받는다.
		assertThat(fakeMattermost.receivedBy("down-17")).hasSize(1);
		assertThat(fakeMattermost.receivedBy("ok-17")).hasSize(2);
	}

	@Test
	void 웹후크가_400이면_지워진_웹후크일_수_있어_다음_웹후크로_보낸다() throws Exception {
		steps.sendSsafyCode(steps.verifyEmail(newEmail()), 18, newMattermostId()).andExpect(status().isCreated());
		steps.sendSsafyCode(steps.verifyEmail(newEmail()), 18, newMattermostId()).andExpect(status().isCreated());

		assertThat(fakeMattermost.receivedBy("bad-18")).hasSize(1);
		assertThat(fakeMattermost.receivedBy("ok-18")).hasSize(2);
	}

	@Test
	void 모든_웹후크가_400이면_아이디가_틀린_것으로_보고_같은_성공_응답을_준다() throws Exception {
		steps.sendSsafyCode(steps.verifyEmail(newEmail()), 19, newMattermostId())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.expiresAt").value(now.plus(Duration.ofMinutes(5)).toString()));

		assertThat(fakeMattermost.receivedBy("bad-19a")).hasSize(1);
		assertThat(fakeMattermost.receivedBy("bad-19b")).hasSize(1);
	}

	@Test
	void 모든_웹후크가_500이나_연결_오류면_503_VERIFICATION_SEND_FAILED이고_바로_다시_보낼_수_있다() throws Exception {
		Cookie signup = steps.verifyEmail(newEmail());
		String failed = """
				{
				  "success": false,
				  "data": null,
				  "error": {
				    "code": "VERIFICATION_SEND_FAILED",
				    "message": "인증 코드를 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.",
				    "fields": null,
				    "detail": null
				  }
				}
				""";

		steps.sendSsafyCode(signup, 20, newMattermostId())
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().json(failed, JsonCompareMode.STRICT));
		// 보내지 못한 코드는 재전송 간격을 세지 않는다. 429가 아니라 다시 보내기를 시도한다.
		steps.sendSsafyCode(signup, 16, newMattermostId()).andExpect(status().isCreated());
	}

	@Test
	void 기수에_웹후크가_없으면_503이다() throws Exception {
		steps.sendSsafyCode(steps.verifyEmail(newEmail()), 21, newMattermostId())
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error.code").value("VERIFICATION_SEND_FAILED"));
	}

	@Test
	void 가입_쿠키가_없거나_1시간이_지났으면_409_SIGNUP_EXPIRED다() throws Exception {
		String expired = """
				{
				  "success": false,
				  "data": null,
				  "error": {
				    "code": "SIGNUP_EXPIRED",
				    "message": "가입 시간이 지났습니다. 처음부터 다시 진행해 주세요.",
				    "fields": null,
				    "detail": null
				  }
				}
				""";
		steps.sendSsafyCode(null, 16, newMattermostId())
				.andExpect(status().isConflict())
				.andExpect(content().json(expired, JsonCompareMode.STRICT));

		Cookie signup = steps.verifyEmail(newEmail());
		clock.advance(Duration.ofHours(1));
		steps.sendSsafyCode(signup, 16, newMattermostId())
				.andExpect(status().isConflict())
				.andExpect(content().json(expired, JsonCompareMode.STRICT));
	}

	@Test
	void 고를_수_없는_기수나_아이디_형식이_틀리면_400_INVALID_INPUT이다() throws Exception {
		Cookie signup = steps.verifyEmail(newEmail());

		steps.sendSsafyCode(signup, 99, newMattermostId())
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "INVALID_INPUT",
						    "message": "입력값이 올바르지 않습니다.",
						    "fields": [ { "field": "generation", "message": "고를 수 있는 기수가 아닙니다." } ],
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
		steps.sendSsafyCode(signup, 16, "mole!kim")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("mattermostId"));
		assertThat(fakeMattermost.received()).isEmpty();
	}

	@Test
	void 다른_계정이_가입에_쓴_아이디는_409_MATTERMOST_ID_DUPLICATED다() throws Exception {
		String id = newMattermostId();
		long userId = testUsers.createEmailUser(newEmail(), "mole1234!");
		testUsers.setMattermostId(userId, id);

		steps.sendSsafyCode(steps.verifyEmail(newEmail()), 16, id)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MATTERMOST_ID_DUPLICATED"));
		assertThat(fakeMattermost.received()).isEmpty();
	}

	@Test
	void 재전송_간격_안에_다시_보내면_429이고_지난_뒤에는_보낸다() throws Exception {
		Cookie signup = steps.verifyEmail(newEmail());
		String id = newMattermostId();
		steps.sendSsafyCode(signup, 16, id).andExpect(status().isCreated());

		clock.advance(Duration.ofSeconds(59));
		steps.sendSsafyCode(signup, 16, id)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));

		clock.advance(Duration.ofSeconds(1));
		steps.sendSsafyCode(signup, 16, id).andExpect(status().isCreated());
		assertThat(fakeMattermost.receivedBy("ok-16")).hasSize(2);
	}

	@Test
	void 이메일_인증을_다시_하면_이전_가입_기록은_쓸_수_없다() throws Exception {
		String email = newEmail();
		Cookie first = steps.verifyEmail(email);

		clock.advance(Duration.ofMinutes(1));
		steps.sendEmailCode(email).andExpect(status().isCreated());
		String code = mailpit.latestTo(email).code();
		Cookie second = SignupSteps.signupCookie(steps.checkEmailCode(email, code, first)
				.andExpect(status().isOk())
				.andReturn().getResponse());

		assertThat(second.getValue()).isNotEqualTo(first.getValue());
		steps.sendSsafyCode(first, 16, newMattermostId())
				.andExpect(jsonPath("$.error.code").value("SIGNUP_EXPIRED"));
		steps.sendSsafyCode(second, 16, newMattermostId()).andExpect(status().isCreated());
	}

}
