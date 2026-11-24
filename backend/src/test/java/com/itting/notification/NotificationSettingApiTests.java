package com.itting.notification;

import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/me/notification-settings}, {@code PATCH /api/v1/me/notification-settings}.
 * 빨간 점이 바뀔 때 실시간 소식을 내는지는 커밋이 있어야 하므로 {@link NotificationSettingEventTests}에서 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NotificationSettingApiTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Autowired
	NotificationSteps steps;

	long me;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		me = steps.createMember("me@example.com", PASSWORD, "두더지왕");
		session = login("me@example.com");
	}

	// GET /api/v1/me/notification-settings

	@Test
	void 줄이_없는_계정은_다섯_값이_모두_켜져_있고_푸시_공개_키는_null이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/notification-settings").cookie(session))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": {
						    "isMeetupOn": true,
						    "isChatOn": true,
						    "isCommunityOn": true,
						    "isBurrowOn": true,
						    "isNoticeOn": true,
						    "pushPublicKey": null
						  },
						  "error": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 줄이_있으면_그_값을_준다() throws Exception {
		steps.setting(me, true, false, true, false, true);

		mockMvc.perform(get("/api/v1/me/notification-settings").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isMeetupOn").value(true))
				.andExpect(jsonPath("$.data.isChatOn").value(false))
				.andExpect(jsonPath("$.data.isCommunityOn").value(true))
				.andExpect(jsonPath("$.data.isBurrowOn").value(false))
				.andExpect(jsonPath("$.data.isNoticeOn").value(true));
	}

	@Test
	void 설정은_내_것만_준다() throws Exception {
		long other = steps.createMember("other@example.com", PASSWORD, "새벽산책");
		steps.setting(other, false, false, false, false, false);

		mockMvc.perform(get("/api/v1/me/notification-settings").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isMeetupOn").value(true))
				.andExpect(jsonPath("$.data.isNoticeOn").value(true));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/notification-settings"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		update(null, true, true, true, true, true)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 온보딩을_마치지_않은_계정은_403이다() throws Exception {
		testUsers.createEmailUser("new@example.com", PASSWORD);
		Cookie newcomer = login("new@example.com");

		mockMvc.perform(get("/api/v1/me/notification-settings").cookie(newcomer))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
		update(newcomer, true, true, true, true, true)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	// PATCH /api/v1/me/notification-settings

	@Test
	void 처음_바꾸면_줄을_만들고_저장한_값을_주며_다시_읽어도_같다() throws Exception {
		assertThat(settingRows()).isZero();

		update(session, true, true, false, true, true)
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": {
						    "isMeetupOn": true,
						    "isChatOn": true,
						    "isCommunityOn": false,
						    "isBurrowOn": true,
						    "isNoticeOn": true
						  },
						  "error": null
						}
						""", JsonCompareMode.STRICT));

		assertThat(settingRows()).isEqualTo(1);
		mockMvc.perform(get("/api/v1/me/notification-settings").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isMeetupOn").value(true))
				.andExpect(jsonPath("$.data.isCommunityOn").value(false))
				.andExpect(jsonPath("$.data.isNoticeOn").value(true));
	}

	@Test
	void 이미_줄이_있으면_그_줄을_바꾼다() throws Exception {
		steps.setting(me, true, true, false, true, true);

		update(session, false, true, true, true, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isMeetupOn").value(false))
				.andExpect(jsonPath("$.data.isCommunityOn").value(true))
				.andExpect(jsonPath("$.data.isNoticeOn").value(false));

		assertThat(settingRows()).isEqualTo(1);
		mockMvc.perform(get("/api/v1/me/notification-settings").cookie(session))
				.andExpect(jsonPath("$.data.isMeetupOn").value(false))
				.andExpect(jsonPath("$.data.isChatOn").value(true))
				.andExpect(jsonPath("$.data.isCommunityOn").value(true))
				.andExpect(jsonPath("$.data.isBurrowOn").value(true))
				.andExpect(jsonPath("$.data.isNoticeOn").value(false));
	}

	@Test
	void 다른_사람의_설정은_바뀌지_않는다() throws Exception {
		long other = steps.createMember("other@example.com", PASSWORD, "새벽산책");
		steps.setting(other, true, true, true, true, true);

		update(session, false, false, false, false, false).andExpect(status().isOk());

		Boolean otherMeetupOn = jdbcTemplate.queryForObject(
				"SELECT meetup_on FROM notification_setting WHERE user_id = ?", Boolean.class, other);
		assertThat(otherMeetupOn).isTrue();
	}

	@Test
	void 끄기_전에_받은_알림은_지우지_않고_빨간_점에서만_빠진다() throws Exception {
		long other = steps.createMember("other@example.com", PASSWORD, "새벽산책");
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		steps.insertPostCommented(me, other, 10L, now.minus(Duration.ofMinutes(1)), null);
		mockMvc.perform(get("/api/v1/me/notifications/unread-count").cookie(session))
				.andExpect(jsonPath("$.data.unreadCount").value(1))
				.andExpect(jsonPath("$.data.hasUnread").value(true));

		update(session, true, true, false, true, true).andExpect(status().isOk());

		assertThat(steps.count(me)).isEqualTo(1);
		mockMvc.perform(get("/api/v1/me/notifications/unread-count").cookie(session))
				.andExpect(jsonPath("$.data.unreadCount").value(1))
				.andExpect(jsonPath("$.data.hasUnread").value(false));
		mockMvc.perform(get("/api/v1/me/notifications").cookie(session))
				.andExpect(jsonPath("$.data.items[0].type").value("POST_COMMENTED"));
	}

	@Test
	void 값_하나가_빠지면_400_INVALID_INPUT과_fields를_주고_줄을_만들지_않는다() throws Exception {
		patchBody(session, """
				{ "isMeetupOn": true, "isChatOn": true, "isCommunityOn": false, "isBurrowOn": true }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("isNoticeOn")));
		patchBody(session, "{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("isMeetupOn")))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("isChatOn")))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("isCommunityOn")))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("isBurrowOn")))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("isNoticeOn")));

		assertThat(settingRows()).isZero();
	}

	@Test
	void 값이_null이거나_참거짓이_아니면_400_INVALID_INPUT이다() throws Exception {
		patchBody(session, """
				{ "isMeetupOn": true, "isChatOn": true, "isCommunityOn": null, "isBurrowOn": true, "isNoticeOn": true }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(hasItem("isCommunityOn")));
		patchBody(session, """
				{ "isMeetupOn": "yes", "isChatOn": true, "isCommunityOn": true, "isBurrowOn": true, "isNoticeOn": true }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		assertThat(settingRows()).isZero();
	}

	private int settingRows() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification_setting WHERE user_id = ?",
				Integer.class, me);
	}

	private ResultActions update(Cookie cookie, boolean meetup, boolean chat, boolean community, boolean burrow,
			boolean notice) throws Exception {
		return patchBody(cookie, """
				{
				  "isMeetupOn": %s,
				  "isChatOn": %s,
				  "isCommunityOn": %s,
				  "isBurrowOn": %s,
				  "isNoticeOn": %s
				}
				""".formatted(meetup, chat, community, burrow, notice));
	}

	private ResultActions patchBody(Cookie cookie, String body) throws Exception {
		var request = patch("/api/v1/me/notification-settings")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
		return mockMvc.perform(cookie == null ? request : request.cookie(cookie));
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
