package com.itting.user;

import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /api/v1/nicknames/check}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NicknameApiTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	TestUsers testUsers;

	@Autowired
	OnboardingSteps steps;

	@BeforeEach
	void clearHolds() {
		steps.clearNicknameHolds();
	}

	@Test
	void 쓸_수_있으면_200과_풀리는_시각을_주고_5분_동안_맡아_둔다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		Instant before = Instant.now();

		String response = check(login("mole@example.com"), "두더지왕")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		// 응답의 시각은 초 단위로 자른다(JacksonConfig).
		Instant expiresAt = Instant.parse(JsonPath.read(response, "$.data.expiresAt"));
		assertThat(expiresAt).isBetween(before.plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.SECONDS),
				Instant.now().plus(Duration.ofMinutes(5)));
		assertThat(steps.holder("두더지왕")).isEqualTo(String.valueOf(userId));
		assertThat(redis.getExpire(OnboardingSteps.HOLD_KEY + "두더지왕")).isBetween(290L, 300L);
	}

	@Test
	void 앞뒤_공백은_지우고_본다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);

		check(login("mole@example.com"), "  두더지왕  ").andExpect(status().isOk());

		assertThat(steps.holder("두더지왕")).isEqualTo(String.valueOf(userId));
	}

	@Test
	void 같은_계정이_같은_닉네임을_다시_확인하면_200이고_시간을_새로_센다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login("mole@example.com");
		check(session, "두더지왕").andExpect(status().isOk());
		redis.expire(OnboardingSteps.HOLD_KEY + "두더지왕", Duration.ofSeconds(10));

		check(session, "두더지왕").andExpect(status().isOk());

		assertThat(redis.getExpire(OnboardingSteps.HOLD_KEY + "두더지왕")).isGreaterThan(290L);
	}

	@Test
	void 다른_계정이_맡아_둔_닉네임은_409이고_그_계정이_다른_닉네임을_확인하면_풀린다() throws Exception {
		testUsers.createEmailUser("first@example.com", PASSWORD);
		long second = testUsers.createEmailUser("second@example.com", PASSWORD);
		Cookie first = login("first@example.com");
		Cookie secondSession = login("second@example.com");
		check(first, "두더지왕").andExpect(status().isOk());

		check(secondSession, "두더지왕")
				.andExpect(status().isConflict())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": { "code": "NICKNAME_DUPLICATED", "message": "이미 사용 중인 닉네임입니다.", "fields": null, "detail": null }
						}
						""", JsonCompareMode.STRICT));

		check(first, "땅굴대장").andExpect(status().isOk());
		check(secondSession, "두더지왕").andExpect(status().isOk());
		assertThat(steps.holder("두더지왕")).isEqualTo(String.valueOf(second));
	}

	@Test
	void 맡아_둔_시간이_지나면_다른_계정이_맡을_수_있다() throws Exception {
		testUsers.createEmailUser("first@example.com", PASSWORD);
		testUsers.createEmailUser("second@example.com", PASSWORD);
		check(login("first@example.com"), "두더지왕").andExpect(status().isOk());
		redis.delete(OnboardingSteps.HOLD_KEY + "두더지왕");

		check(login("second@example.com"), "두더지왕").andExpect(status().isOk());
	}

	@Test
	void 다른_사람이_쓰고_있는_닉네임은_대소문자가_달라도_409다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		long other = testUsers.createEmailUser("other@example.com", PASSWORD);
		jdbcTemplate.update("UPDATE user_profile SET nickname = 'Mole' WHERE user_id = ?", other);
		Cookie session = login("mole@example.com");

		check(session, "MOLE").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
		check(session, "mole").andExpect(status().isConflict());
	}

	@Test
	void 다른_계정이_맡은_닉네임은_대소문자가_달라도_409다() throws Exception {
		testUsers.createEmailUser("first@example.com", PASSWORD);
		testUsers.createEmailUser("second@example.com", PASSWORD);
		check(login("first@example.com"), "Mole").andExpect(status().isOk());

		check(login("second@example.com"), "mOLE").andExpect(status().isConflict());
	}

	@Test
	void 내_지금_닉네임은_쓸_수_있다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		jdbcTemplate.update("UPDATE user_profile SET nickname = '두더지왕' WHERE user_id = ?", userId);

		check(login("mole@example.com"), "두더지왕").andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "두더지 왕", "ㅋㅋ", "mole!", "두더지😀", "가나다라마바사아자차카타파" })
	void 규칙에_맞지_않으면_400_INVALID_INPUT이다(String nickname) throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);

		check(login("mole@example.com"), nickname)
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "INVALID_INPUT",
						    "message": "입력값이 올바르지 않습니다.",
						    "fields": [ { "field": "nickname", "message": "한글, 영어, 숫자로 1 ~ 12자여야 합니다." } ],
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 열두_자까지는_쓸_수_있다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);

		check(login("mole@example.com"), "가나다라마바사아자차카1").andExpect(status().isOk());
	}

	@Test
	void 닉네임이_없으면_400이다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);

		mockMvc.perform(post("/api/v1/nicknames/check")
						.cookie(login("mole@example.com"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("nickname"));
	}

	@Test
	void 로그인하지_않았으면_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(post("/api/v1/nicknames/check")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "nickname": "두더지왕" }
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private ResultActions check(Cookie session, String nickname) throws Exception {
		return mockMvc.perform(post("/api/v1/nicknames/check")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "nickname": "%s" }
						""".formatted(nickname)));
	}

	private Cookie login(String email) throws Exception {
		return sessionCookie(mockMvc.perform(post("/api/v1/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "%s", "password": "%s" }
								""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

}
