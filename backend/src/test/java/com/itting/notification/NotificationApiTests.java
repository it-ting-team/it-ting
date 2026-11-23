package com.itting.notification;

import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.itting.support.NotificationSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 API 다섯 개. 다른 영역의 읽기 구현(요청 수, 갈 곳의 이름)이 없을 때다. 있을 때는
 * {@link NotificationReaderApiTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class NotificationApiTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestUsers testUsers;

	@Autowired
	NotificationSteps steps;

	@PersistenceContext
	EntityManager entityManager;

	Instant now;

	long me;

	long other;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		me = steps.createMember("me@example.com", PASSWORD, "두더지왕");
		other = steps.createMember("other@example.com", PASSWORD, "새벽산책");
		session = login("me@example.com");
	}

	@Test
	void 팝업은_요청_알림을_빼고_안_읽은_것_먼저_5개와_안_읽은_수를_준다() throws Exception {
		long readNewest = steps.insertPostCommented(me, other, 10L, minutesAgo(1), minutesAgo(1));
		steps.insert(me, "MEETUP_APPLICATION_RECEIVED", "MEETUP", 3L, other, minutesAgo(2), null);
		steps.insert(me, "CHAT_REQUEST_RECEIVED", "CHAT_REQUEST", 4L, other, minutesAgo(3), null);
		long unread1 = steps.insertPostCommented(me, other, 11L, minutesAgo(4), null);
		long unread2 = steps.insert(me, "MEETUP_START_REMINDER", "MEETUP", 23L, null, minutesAgo(5), null);
		long read2 = steps.insertPostCommented(me, other, 12L, minutesAgo(6), minutesAgo(1));
		long read3 = steps.insert(me, "GUESTBOOK_ENTRY_CREATED", "GUESTBOOK", null, other, minutesAgo(7),
				minutesAgo(1));
		steps.insertPostCommented(me, other, 13L, minutesAgo(8), minutesAgo(1));
		steps.insertPostCommented(me, other, 14L, now.minus(Duration.ofDays(31)), null);
		steps.insertPostCommented(other, me, 15L, minutesAgo(1), null);

		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.meetupRequestCount").value(0))
				.andExpect(jsonPath("$.data.chatRequestCount").value(0))
				.andExpect(jsonPath("$.data.latestChatRequester").value(nullValue()))
				.andExpect(jsonPath("$.data.recentItems[*].id").value(contains(
						(int) unread1, (int) unread2, (int) readNewest, (int) read2, (int) read3)))
				.andExpect(jsonPath("$.data.recentItems[0].type").value("POST_COMMENTED"))
				.andExpect(jsonPath("$.data.recentItems[0].category").value("COMMUNITY"))
				.andExpect(jsonPath("$.data.recentItems[0].actor.id").value(other))
				.andExpect(jsonPath("$.data.recentItems[0].actor.nickname").value("새벽산책"))
				.andExpect(jsonPath("$.data.recentItems[0].actor.mole.face").value("GLASSES_ROUND"))
				.andExpect(jsonPath("$.data.recentItems[0].actor.mole.head").value(nullValue()))
				.andExpect(jsonPath("$.data.recentItems[0].actor.campusName").value("서울"))
				.andExpect(jsonPath("$.data.recentItems[0].actor.generation").value(15))
				.andExpect(jsonPath("$.data.recentItems[0].actor.floor").value(2))
				.andExpect(jsonPath("$.data.recentItems[0].actor.isWithdrawn").value(false))
				.andExpect(jsonPath("$.data.recentItems[0].target.type").value("POST"))
				.andExpect(jsonPath("$.data.recentItems[0].target.id").value(11))
				.andExpect(jsonPath("$.data.recentItems[0].target.title").value(nullValue()))
				.andExpect(jsonPath("$.data.recentItems[0].target.startsAt").value(nullValue()))
				.andExpect(jsonPath("$.data.recentItems[0].isRead").value(false))
				.andExpect(jsonPath("$.data.recentItems[0].createdAt").value(minutesAgo(4).truncatedTo(ChronoUnit.SECONDS).toString()))
				.andExpect(jsonPath("$.data.recentItems[1].actor").value(nullValue()))
				.andExpect(jsonPath("$.data.recentItems[2].isRead").value(true))
				.andExpect(jsonPath("$.data.recentItems[4].target.type").value("GUESTBOOK"))
				.andExpect(jsonPath("$.data.recentItems[4].target.id").value(nullValue()))
				// 안 읽은 요청 알림 둘도 센다. 31일 된 알림은 세지 않는다.
				.andExpect(jsonPath("$.data.unreadCount").value(4));
	}

	@Test
	void 팝업에_최근_알림이_없으면_빈_목록이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recentItems").isEmpty())
				.andExpect(jsonPath("$.data.unreadCount").value(0));
	}

	@Test
	void 탈퇴한_사람이_일으킨_알림은_id와_isWithdrawn만_준다() throws Exception {
		steps.insertPostCommented(me, other, 10L, minutesAgo(1), null);
		testUsers.withdraw(other);
		entityManager.createNativeQuery("DELETE FROM user_profile WHERE user_id = " + other).executeUpdate();

		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recentItems[0].actor.id").value(other))
				.andExpect(jsonPath("$.data.recentItems[0].actor.isWithdrawn").value(true))
				.andExpect(jsonPath("$.data.recentItems[0].actor.nickname").value(nullValue()))
				.andExpect(jsonPath("$.data.recentItems[0].actor.campusName").value(nullValue()))
				.andExpect(jsonPath("$.data.recentItems[0].actor.generation").value(nullValue()))
				.andExpect(jsonPath("$.data.recentItems[0].actor.mole.face").value(nullValue()));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/notifications/popup"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/v1/me/notifications"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/me/notifications/unread-count"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/notifications/1/read"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/me/notifications/read-all"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 온보딩을_마치지_않은_계정은_403이다() throws Exception {
		testUsers.createEmailUser("new@example.com", PASSWORD);

		mockMvc.perform(get("/api/v1/me/notifications/unread-count").cookie(login("new@example.com")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	@Test
	void 목록은_20개씩_주고_커서로_이어_주며_사이에_읽어도_겹치거나_빠지지_않는다() throws Exception {
		List<Long> unread = new ArrayList<>();
		List<Long> read = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			unread.add(steps.insertPostCommented(me, other, 100L + i, minutesAgo(10 + i), null));
		}
		for (int i = 0; i < 10; i++) {
			read.add(steps.insertPostCommented(me, other, 200L + i, minutesAgo(5 + i * 2), minutesAgo(1)));
		}
		steps.insertPostCommented(me, other, 300L, now.minus(Duration.ofDays(31)), minutesAgo(1));

		String first = mockMvc.perform(get("/api/v1/me/notifications").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(20)))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();
		List<Integer> firstIds = JsonPath.read(first, "$.data.items[*].id");
		List<Integer> expected = new ArrayList<>();
		unread.forEach(id -> expected.add(id.intValue()));
		read.forEach(id -> expected.add(id.intValue()));
		assertThat(firstIds).containsExactlyElementsOf(expected.subList(0, 20));

		// 이미 받은 안 읽은 알림을 읽고, 새 알림이 생겨도 다음 쪽은 처음 순서 그대로 이어진다.
		mockMvc.perform(post("/api/v1/notifications/{id}/read", unread.get(3)).cookie(session))
				.andExpect(status().isOk());
		steps.insertPostCommented(me, other, 400L, now.plusSeconds(1), null);

		String cursor = JsonPath.read(first, "$.data.nextCursor");
		String second = mockMvc.perform(get("/api/v1/me/notifications").cookie(session).param("cursor", cursor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
				.andReturn().getResponse().getContentAsString();
		List<Integer> secondIds = JsonPath.read(second, "$.data.items[*].id");
		assertThat(secondIds).containsExactlyElementsOf(expected.subList(20, 25));

		Set<Integer> all = new HashSet<>(firstIds);
		all.addAll(secondIds);
		assertThat(all).hasSize(25);
	}

	@Test
	void 커서가_안_읽은_구간_끝에_걸려도_읽은_구간을_처음부터_준다() throws Exception {
		long unread = steps.insertPostCommented(me, other, 1L, minutesAgo(10), null);
		long read1 = steps.insertPostCommented(me, other, 2L, minutesAgo(1), minutesAgo(1));
		long read2 = steps.insertPostCommented(me, other, 3L, minutesAgo(2), minutesAgo(1));

		String first = mockMvc.perform(get("/api/v1/me/notifications").cookie(session).param("size", "1"))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) unread)))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();
		String second = mockMvc.perform(get("/api/v1/me/notifications").cookie(session).param("size", "1")
						.param("cursor", JsonPath.<String>read(first, "$.data.nextCursor")))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) read1)))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();
		mockMvc.perform(get("/api/v1/me/notifications").cookie(session).param("size", "1")
						.param("cursor", JsonPath.<String>read(second, "$.data.nextCursor")))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) read2)))
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	void 갈래를_고르면_그_갈래만_주고_요청_알림도_준다() throws Exception {
		long chatRequest = steps.insert(me, "CHAT_REQUEST_RECEIVED", "CHAT_REQUEST", 4L, other, minutesAgo(1), null);
		long chatMessage = steps.insert(me, "CHAT_MESSAGE_RECEIVED", "CHAT_ROOM", 9L, other, minutesAgo(2), null);
		steps.insertPostCommented(me, other, 10L, minutesAgo(3), null);

		mockMvc.perform(get("/api/v1/me/notifications").cookie(session).param("category", "CHAT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) chatRequest, (int) chatMessage)))
				.andExpect(jsonPath("$.data.items[0].target.type").value("CHAT_REQUEST"))
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	void 목록의_입력이_틀리면_400이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/notifications").cookie(session).param("size", "51"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("size"));
		mockMvc.perform(get("/api/v1/me/notifications").cookie(session).param("size", "0"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/me/notifications").cookie(session).param("category", "FOO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("category"));
		mockMvc.perform(get("/api/v1/me/notifications").cookie(session).param("cursor", "not-a-cursor"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("cursor"));
	}

	@Test
	void 안_읽은_수는_모든_갈래를_세고_빨간_점은_켜_둔_갈래만_본다() throws Exception {
		steps.insert(me, "CHAT_MESSAGE_RECEIVED", "CHAT_ROOM", 9L, other, minutesAgo(1), null);
		steps.insertPostCommented(me, other, 10L, minutesAgo(2), minutesAgo(1));
		steps.setting(me, true, false, true, true, true);

		mockMvc.perform(get("/api/v1/me/notifications/unread-count").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.unreadCount").value(1))
				.andExpect(jsonPath("$.data.hasUnread").value(false));

		steps.insertPostCommented(me, other, 11L, minutesAgo(1), null);
		mockMvc.perform(get("/api/v1/me/notifications/unread-count").cookie(session))
				.andExpect(jsonPath("$.data.unreadCount").value(2))
				.andExpect(jsonPath("$.data.hasUnread").value(true));
	}

	@Test
	void 알림_하나를_읽으면_읽음이_되고_줄어든_수를_준다() throws Exception {
		long target = steps.insertPostCommented(me, other, 10L, minutesAgo(2), null);
		steps.insertPostCommented(me, other, 11L, minutesAgo(1), null);

		mockMvc.perform(post("/api/v1/notifications/{id}/read", target).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.unreadCount").value(1))
				.andExpect(jsonPath("$.data.hasUnread").value(true));
		entityManager.flush();
		Instant readAt = steps.readAt(target);
		assertThat(readAt).isNotNull();

		// 이미 읽은 알림은 그대로 두고 성공한다.
		mockMvc.perform(post("/api/v1/notifications/{id}/read", target).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.unreadCount").value(1));
		entityManager.flush();
		assertThat(steps.readAt(target)).isEqualTo(readAt);
	}

	@Test
	void 남의_알림_없는_알림_30일이_지난_알림은_404다() throws Exception {
		long others = steps.insertPostCommented(other, me, 10L, minutesAgo(1), null);
		long old = steps.insertPostCommented(me, other, 11L, now.minus(Duration.ofDays(31)), null);

		for (long id : new long[] { others, old, 999_999_999L }) {
			mockMvc.perform(post("/api/v1/notifications/{id}/read", id).cookie(session))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
		}
		entityManager.flush();
		assertThat(steps.readAt(others)).isNull();
	}

	@Test
	void 모두_읽음은_내_알림만_모두_읽음으로_바꾼다() throws Exception {
		long chat = steps.insert(me, "CHAT_MESSAGE_RECEIVED", "CHAT_ROOM", 9L, other, minutesAgo(1), null);
		long post = steps.insertPostCommented(me, other, 10L, minutesAgo(2), null);
		long others = steps.insertPostCommented(other, me, 11L, minutesAgo(1), null);

		mockMvc.perform(post("/api/v1/me/notifications/read-all").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.unreadCount").value(0))
				.andExpect(jsonPath("$.data.hasUnread").value(false));
		entityManager.flush();

		assertThat(steps.readAt(chat)).isNotNull();
		assertThat(steps.readAt(post)).isNotNull();
		assertThat(steps.readAt(others)).isNull();
		mockMvc.perform(get("/api/v1/me/notifications/unread-count").cookie(session))
				.andExpect(jsonPath("$.data.unreadCount").value(0));
	}

	private Instant minutesAgo(int minutes) {
		return now.minus(Duration.ofMinutes(minutes));
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
