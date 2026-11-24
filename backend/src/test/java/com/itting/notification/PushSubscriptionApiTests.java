package com.itting.notification;

import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.notification.dto.PushSubscriptionRequest;
import com.itting.notification.service.PushSubscriptionService;
import com.itting.support.NotificationSteps;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /api/v1/me/push-subscriptions}, {@code DELETE /api/v1/me/push-subscriptions/{pushSubscriptionId}}.
 * 같은 주소가 동시에 들어오는 경우는 커밋이 있어야 하므로 {@link PushSubscriptionConcurrencyTests}에서 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PushSubscriptionApiTests {

	private static final String PASSWORD = "mole1234!";

	private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/c1KrmpTuRm8:APA91bHexample";

	private static final String P256DH = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM";

	private static final String AUTH = "tBHItJI5svbpez7KI4CCXg"; // gitleaks:allow (테스트용 예시 값)

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Autowired
	NotificationSteps steps;

	@Autowired
	PushSubscriptionService pushSubscriptionService;

	long me;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		me = steps.createMember("me@example.com", PASSWORD, "두더지왕");
		session = login("me@example.com");
	}

	// POST /api/v1/me/push-subscriptions

	@Test
	void 새_브라우저를_등록하면_201과_id를_주고_줄을_만든다() throws Exception {
		register(session, ENDPOINT, P256DH, AUTH)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.pushSubscriptionId").isNumber())
				.andExpect(jsonPath("$.error").isEmpty());
		Map<String, Object> row = row(ENDPOINT);
		assertThat(row.get("user_id")).isEqualTo(me);
		assertThat(row.get("p256dh")).isEqualTo(P256DH);
		assertThat(row.get("auth")).isEqualTo(AUTH);
		assertThat(row.get("created_at")).isNotNull();
		assertThat(rows()).isEqualTo(1);
	}

	@Test
	void 응답의_data는_pushSubscriptionId_하나이고_줄의_id와_같다() throws Exception {
		ResultActions result = register(session, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated());

		long id = idOf(result);
		result.andExpect(jsonPath("$.data.length()").value(1));
		assertThat(row(ENDPOINT).get("id")).isEqualTo(id);
	}

	@Test
	void 같은_브라우저를_다시_등록하면_200과_같은_id를_주고_키만_바꾼다() throws Exception {
		long first = idOf(register(session, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated()));

		long second = idOf(register(session, ENDPOINT, "BNewKeyNewKeyNewKey", "newAuthValue")
				.andExpect(status().isOk()));

		assertThat(second).isEqualTo(first);
		assertThat(rows()).isEqualTo(1);
		Map<String, Object> row = row(ENDPOINT);
		assertThat(row.get("user_id")).isEqualTo(me);
		assertThat(row.get("p256dh")).isEqualTo("BNewKeyNewKeyNewKey");
		assertThat(row.get("auth")).isEqualTo("newAuthValue");
	}

	@Test
	void 다른_계정이_등록해_둔_브라우저를_등록하면_같은_id로_내_것이_된다() throws Exception {
		long other = steps.createMember("other@example.com", PASSWORD, "새벽산책");
		Cookie otherSession = login("other@example.com");
		long id = idOf(register(otherSession, ENDPOINT, "BOtherKeyOtherKey", "otherAuth")
				.andExpect(status().isCreated()));
		assertThat(row(ENDPOINT).get("user_id")).isEqualTo(other);

		long takenOver = idOf(register(session, ENDPOINT, P256DH, AUTH).andExpect(status().isOk()));

		assertThat(takenOver).isEqualTo(id);
		assertThat(rows()).isEqualTo(1);
		Map<String, Object> row = row(ENDPOINT);
		assertThat(row.get("user_id")).as("브라우저 하나는 한 계정의 푸시만 받는다").isEqualTo(me);
		assertThat(row.get("p256dh")).isEqualTo(P256DH);
		assertThat(row.get("auth")).isEqualTo(AUTH);
		assertThat(countOf(other)).isZero();
	}

	@Test
	void 한_계정이_여러_브라우저를_등록할_수_있다() throws Exception {
		long first = idOf(register(session, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated()));
		long second = idOf(register(session, "https://updates.push.services.mozilla.com/wpush/v2/abc", P256DH, AUTH)
				.andExpect(status().isCreated()));

		assertThat(second).isNotEqualTo(first);
		assertThat(countOf(me)).isEqualTo(2);
	}

	@Test
	void 주소의_대소문자는_다른_주소로_본다() throws Exception {
		register(session, "https://push.example.com/send/AbCd", P256DH, AUTH).andExpect(status().isCreated());
		register(session, "https://push.example.com/send/abcd", P256DH, AUTH).andExpect(status().isCreated());

		assertThat(rows()).isEqualTo(2);
	}

	@Test
	void 주소는_2000자까지_받고_키는_끝의_등호가_있어도_받는다() throws Exception {
		String longest = "https://push.example.com/" + "a".repeat(2000 - "https://push.example.com/".length());
		assertThat(longest).hasSize(2000);

		register(session, longest, P256DH + "=", AUTH + "==").andExpect(status().isCreated());

		assertThat(row(longest).get("p256dh")).isEqualTo(P256DH + "=");
	}

	@Test
	void http_주소는_400_INVALID_INPUT이고_줄을_만들지_않는다() throws Exception {
		register(session, "http://push.example.com/send/abc", P256DH, AUTH)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("endpoint")));

		assertThat(rows()).isZero();
	}

	@Test
	void 주소가_2001자이면_400_INVALID_INPUT이다() throws Exception {
		String tooLong = "https://push.example.com/" + "a".repeat(2001 - "https://push.example.com/".length());
		assertThat(tooLong).hasSize(2001);

		register(session, tooLong, P256DH, AUTH)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("endpoint")));

		assertThat(rows()).isZero();
	}

	@Test
	void 주소에_ASCII가_아닌_글자나_공백이_있으면_400이다() throws Exception {
		for (String endpoint : List.of("https://push.example.com/한글", "https://push.example.com/a b",
				"https://push.example.com/a\tb", "https://")) {
			register(session, endpoint, P256DH, AUTH)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
					.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("endpoint")));
		}

		assertThat(rows()).isZero();
	}

	@Test
	void 주소가_없거나_비었으면_400이다() throws Exception {
		postBody("{ \"keys\": { \"p256dh\": \"%s\", \"auth\": \"%s\" } }".formatted(P256DH, AUTH))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("endpoint")));
		register(session, "", P256DH, AUTH)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("endpoint")));

		assertThat(rows()).isZero();
	}

	@Test
	void 키가_없으면_400이고_어느_칸인지_알려_준다() throws Exception {
		postBody("{ \"endpoint\": \"%s\" }".formatted(ENDPOINT))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("keys")));
		postBody("{ \"endpoint\": \"%s\", \"keys\": { \"auth\": \"%s\" } }".formatted(ENDPOINT, AUTH))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("keys.p256dh")));
		postBody("{ \"endpoint\": \"%s\", \"keys\": { \"p256dh\": \"%s\" } }".formatted(ENDPOINT, P256DH))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("keys.auth")));
		register(session, ENDPOINT, "  ", AUTH)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("keys.p256dh")));

		assertThat(rows()).isZero();
	}

	@Test
	void 키가_base64url_글이_아니거나_255자를_넘으면_400이다() throws Exception {
		register(session, ENDPOINT, "not base64url!", AUTH)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("keys.p256dh")));
		register(session, ENDPOINT, P256DH, "한글인증값")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("keys.auth")));
		register(session, ENDPOINT, "A".repeat(256), AUTH)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("keys.p256dh")));

		assertThat(rows()).isZero();
	}

	@Test
	void 본문이_JSON이_아니면_400이다() throws Exception {
		postBody("not json")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 로그인하지_않으면_401이고_줄을_만들지_않는다() throws Exception {
		register(null, ENDPOINT, P256DH, AUTH)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(rows()).isZero();
	}

	@Test
	void 온보딩을_마치지_않은_계정은_403이다() throws Exception {
		testUsers.createEmailUser("new@example.com", PASSWORD);
		Cookie newcomer = login("new@example.com");

		register(newcomer, ENDPOINT, P256DH, AUTH)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
		mockMvc.perform(delete("/api/v1/me/push-subscriptions/1").cookie(newcomer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));

		assertThat(rows()).isZero();
	}

	@Test
	void 탈퇴한_계정으로는_등록할_수_없다() {
		testUsers.withdraw(me);
		PushSubscriptionRequest request = new PushSubscriptionRequest(ENDPOINT,
				new PushSubscriptionRequest.Keys(P256DH, AUTH));

		assertThatThrownBy(() -> pushSubscriptionService.register(me, request))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHENTICATED));

		assertThat(rows()).isZero();
	}

	// DELETE /api/v1/me/push-subscriptions/{pushSubscriptionId}

	@Test
	void 내_등록을_지우면_성공이고_그_줄만_사라진다() throws Exception {
		long id = idOf(register(session, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated()));
		register(session, "https://updates.push.services.mozilla.com/wpush/v2/abc", P256DH, AUTH)
				.andExpect(status().isCreated());

		mockMvc.perform(delete("/api/v1/me/push-subscriptions/" + id).cookie(session))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT));

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM push_subscription WHERE id = ?", Integer.class,
				id)).isZero();
		assertThat(countOf(me)).as("다른 브라우저의 등록은 남는다").isEqualTo(1);
	}

	@Test
	void 없는_id도_성공이고_아무것도_지우지_않는다() throws Exception {
		register(session, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated());

		mockMvc.perform(delete("/api/v1/me/push-subscriptions/999999999").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").isEmpty());

		assertThat(rows()).isEqualTo(1);
	}

	@Test
	void 남의_등록은_성공으로_답하되_지워지지_않는다() throws Exception {
		long other = steps.createMember("other@example.com", PASSWORD, "새벽산책");
		Cookie otherSession = login("other@example.com");
		long othersId = idOf(register(otherSession, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated()));

		mockMvc.perform(delete("/api/v1/me/push-subscriptions/" + othersId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").isEmpty());

		assertThat(row(ENDPOINT).get("user_id")).isEqualTo(other);
		assertThat(rows()).isEqualTo(1);
	}

	@Test
	void 지운_뒤에_다시_지워도_성공이다() throws Exception {
		long id = idOf(register(session, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated()));

		mockMvc.perform(delete("/api/v1/me/push-subscriptions/" + id).cookie(session)).andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/me/push-subscriptions/" + id).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		assertThat(rows()).isZero();
	}

	@Test
	void 지운_브라우저를_다시_등록하면_새_줄이다() throws Exception {
		long first = idOf(register(session, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated()));
		mockMvc.perform(delete("/api/v1/me/push-subscriptions/" + first).cookie(session)).andExpect(status().isOk());

		long again = idOf(register(session, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated()));

		assertThat(again).isNotEqualTo(first);
		assertThat(rows()).isEqualTo(1);
	}

	@Test
	void id가_숫자가_아니면_400이다() throws Exception {
		mockMvc.perform(delete("/api/v1/me/push-subscriptions/abc").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 로그인하지_않으면_삭제도_401이고_줄은_그대로다() throws Exception {
		long id = idOf(register(session, ENDPOINT, P256DH, AUTH).andExpect(status().isCreated()));

		mockMvc.perform(delete("/api/v1/me/push-subscriptions/" + id))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(rows()).isEqualTo(1);
	}

	private ResultActions register(Cookie cookie, String endpoint, String p256dh, String auth) throws Exception {
		return postBody(cookie, """
				{ "endpoint": "%s", "keys": { "p256dh": "%s", "auth": "%s" } }
				""".formatted(endpoint, p256dh, auth));
	}

	private ResultActions postBody(String body) throws Exception {
		return postBody(session, body);
	}

	private ResultActions postBody(Cookie cookie, String body) throws Exception {
		var request = MockMvcRequestBuilders
				.post("/api/v1/me/push-subscriptions")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
		return mockMvc.perform(cookie == null ? request : request.cookie(cookie));
	}

	private static long idOf(ResultActions actions) throws Exception {
		String body = actions.andReturn().getResponse().getContentAsString();
		Matcher matcher = Pattern.compile("\"pushSubscriptionId\":(\\d+)").matcher(body);
		assertThat(matcher.find()).as(body).isTrue();
		return Long.parseLong(matcher.group(1));
	}

	private Map<String, Object> row(String endpoint) {
		return jdbcTemplate.queryForMap("SELECT * FROM push_subscription WHERE endpoint = ?", endpoint);
	}

	private int rows() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM push_subscription", Integer.class);
	}

	private int countOf(long userId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM push_subscription WHERE user_id = ?", Integer.class,
				userId);
	}

	private Cookie login(String email) throws Exception {
		return sessionCookie(mockMvc.perform(MockMvcRequestBuilders
						.post("/api/v1/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "%s", "password": "%s" }
								""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

}
