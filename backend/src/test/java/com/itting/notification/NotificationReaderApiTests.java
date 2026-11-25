package com.itting.notification;

import static com.itting.support.TestUsers.sessionCookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.itting.notification.domain.NotificationTargetType;
import com.itting.notification.service.ChatRequestReader;
import com.itting.notification.service.MeetupRequestCountReader;
import com.itting.notification.service.NotificationTargetReader;
import com.itting.notification.service.NotificationTargetSummary;
import com.itting.support.CommunitySteps;
import com.itting.support.NotificationSteps;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 영역이 읽기 구현(요청 수, 가장 최근 요청자, 갈 곳의 이름 · 시작 시각)을 등록했을 때 알림 API가 그 값을 싣는지.
 * 모임 · 모임 요청 수는 아직 구현이 없어 가짜를 등록하고, 대화 요청 수는 채팅의 실제 구현이 있어도 이 테스트의 값을 쓰도록 가짜를
 * 먼저 쓰게 한다({@code @Primary}). 글은 커뮤니티가 등록한 실제 구현이 제목을 싣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, NotificationReaderApiTests.FakeReaders.class })
@Transactional
class NotificationReaderApiTests {

	private static final String PASSWORD = "mole1234!";

	static final Instant MEETUP_STARTS_AT = Instant.parse("2026-11-25T10:00:00Z");

	/** 가장 최근 대화 요청자. 테스트마다 정한다. */
	static Long latestRequesterId;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	NotificationSteps steps;

	@Autowired
	CommunitySteps communitySteps;

	Instant now;

	long me;

	long other;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		me = steps.createMember("me@example.com", PASSWORD, "두더지왕");
		other = steps.createMember("other@example.com", PASSWORD, "모카");
		latestRequesterId = null;
		session = sessionCookie(mockMvc.perform(post("/api/v1/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "me@example.com", "password": "%s" }
								""".formatted(PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

	@Test
	void 팝업에_요청_수와_가장_최근_요청자와_갈_곳의_이름을_싣는다() throws Exception {
		latestRequesterId = other;
		long post = communitySteps.insertPost(other, "FREE", "5층 라운지 커피머신", "본문", now);
		long deletedPost = communitySteps.insertPost(other, "FREE", "DELETED", "지운 글", "본문", now);
		steps.insert(me, "MEETUP_START_REMINDER", "MEETUP", 23L, null, now.minus(Duration.ofMinutes(1)), null);
		steps.insertPostCommented(me, other, post, now.minus(Duration.ofMinutes(2)), null);
		steps.insertPostCommented(me, other, deletedPost, now.minus(Duration.ofMinutes(3)), null);

		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.meetupRequestCount").value(2))
				.andExpect(jsonPath("$.data.chatRequestCount").value(3))
				.andExpect(jsonPath("$.data.latestChatRequester.id").value(other))
				.andExpect(jsonPath("$.data.latestChatRequester.nickname").value("모카"))
				.andExpect(jsonPath("$.data.recentItems[0].target.title").value("퇴근길 러닝 크루"))
				.andExpect(jsonPath("$.data.recentItems[0].target.startsAt").value("2026-11-25T10:00:00Z"))
				.andExpect(jsonPath("$.data.recentItems[1].target.id").value(post))
				.andExpect(jsonPath("$.data.recentItems[1].target.title").value("5층 라운지 커피머신"))
				.andExpect(jsonPath("$.data.recentItems[1].target.startsAt").value(nullValue()))
				// 삭제한 글은 이름이 없다.
				.andExpect(jsonPath("$.data.recentItems[2].target.id").value(deletedPost))
				.andExpect(jsonPath("$.data.recentItems[2].target.title").value(nullValue()));
	}

	@Test
	void 대화_요청이_없으면_가장_최근_요청자는_null이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.latestChatRequester").value(nullValue()));
	}

	@Test
	void 목록에도_갈_곳의_이름을_싣는다() throws Exception {
		long post = communitySteps.insertPost(other, "FREE", "5층 라운지 커피머신", "본문", now);
		steps.insertPostCommented(me, other, post, now.minus(Duration.ofMinutes(2)), null);

		mockMvc.perform(get("/api/v1/me/notifications").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].target.title").value("5층 라운지 커피머신"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeReaders {

		@Bean
		MeetupRequestCountReader meetupRequestCountReader() {
			return hostId -> 2;
		}

		/** 채팅이 등록한 실제 구현이 있으므로 이 가짜를 먼저 쓰게 한다. 실제 구현은 {@code ChatRequestPopupApiTests}가 본다. */
		@Bean
		@Primary
		ChatRequestReader chatRequestReader() {
			return new ChatRequestReader() {
				@Override
				public long countReceivedPending(Long userId) {
					return 3;
				}

				@Override
				public Optional<Long> findLatestRequesterId(Long userId) {
					return Optional.ofNullable(latestRequesterId);
				}
			};
		}

		@Bean
		NotificationTargetReader meetupTargetReader() {
			return new NotificationTargetReader() {
				@Override
				public NotificationTargetType targetType() {
					return NotificationTargetType.MEETUP;
				}

				@Override
				public Map<Long, NotificationTargetSummary> read(Collection<Long> targetIds) {
					return Map.of(23L, new NotificationTargetSummary("퇴근길 러닝 크루", MEETUP_STARTS_AT));
				}
			};
		}

	}

}
