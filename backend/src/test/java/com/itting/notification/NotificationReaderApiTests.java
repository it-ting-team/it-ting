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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.itting.notification.domain.NotificationTargetType;
import com.itting.notification.service.ChatRequestReader;
import com.itting.notification.service.MeetupRequestCountReader;
import com.itting.notification.service.NotificationTargetReader;
import com.itting.notification.service.NotificationTargetSummary;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 영역이 읽기 구현(요청 수, 가장 최근 요청자, 갈 곳의 이름 · 시작 시각)을 등록했을 때 알림 API가 그 값을 싣는지.
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
		steps.insert(me, "MEETUP_START_REMINDER", "MEETUP", 23L, null, now.minus(Duration.ofMinutes(1)), null);
		steps.insertPostCommented(me, other, 204L, now.minus(Duration.ofMinutes(2)), null);
		steps.insertPostCommented(me, other, 999L, now.minus(Duration.ofMinutes(3)), null);

		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.meetupRequestCount").value(2))
				.andExpect(jsonPath("$.data.chatRequestCount").value(3))
				.andExpect(jsonPath("$.data.latestChatRequester.id").value(other))
				.andExpect(jsonPath("$.data.latestChatRequester.nickname").value("모카"))
				.andExpect(jsonPath("$.data.recentItems[0].target.title").value("퇴근길 러닝 크루"))
				.andExpect(jsonPath("$.data.recentItems[0].target.startsAt").value("2026-11-25T10:00:00Z"))
				.andExpect(jsonPath("$.data.recentItems[1].target.title").value("글 204"))
				.andExpect(jsonPath("$.data.recentItems[1].target.startsAt").value(nullValue()))
				// 원본이 사라진 글은 이름이 없다.
				.andExpect(jsonPath("$.data.recentItems[2].target.id").value(999))
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
		steps.insertPostCommented(me, other, 204L, now.minus(Duration.ofMinutes(2)), null);

		mockMvc.perform(get("/api/v1/me/notifications").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].target.title").value("글 204"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeReaders {

		@Bean
		MeetupRequestCountReader meetupRequestCountReader() {
			return hostId -> 2;
		}

		@Bean
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

		@Bean
		NotificationTargetReader postTargetReader() {
			return new NotificationTargetReader() {
				@Override
				public NotificationTargetType targetType() {
					return NotificationTargetType.POST;
				}

				@Override
				public Map<Long, NotificationTargetSummary> read(Collection<Long> targetIds) {
					Map<Long, NotificationTargetSummary> titles = new HashMap<>();
					targetIds.stream()
							.filter(id -> id != 999L)
							.forEach(id -> titles.put(id, new NotificationTargetSummary("글 " + id, null)));
					return titles;
				}
			};
		}

	}

}
