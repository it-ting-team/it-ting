package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 팝업의 "채팅 요청" 줄({@code GET /api/v1/me/notifications/popup})이 채팅의 실제 구현({@code ChatRequestReader})으로
 * 받은 요청 수와 가장 최근 요청자를 싣는지. 가짜를 쓰는 {@code NotificationReaderApiTests}와 달리 가짜를 등록하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatRequestPopupApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	MutableClock clock;

	Instant now;

	long me;

	long mocha;

	long night;

	long third;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		me = steps.createMember("me@example.com", "두더지왕");
		mocha = steps.createMember("mocha@example.com", "모카");
		night = steps.createMember("night@example.com", "밤코딩");
		third = steps.createMember("third@example.com", "새벽산책");
		session = steps.login(mockMvc, "me@example.com");
	}

	@Test
	void 받은_요청_수와_가장_최근_요청자를_싣는다() throws Exception {
		chat.insertRequest(night, me, null, "PENDING", now.minus(Duration.ofDays(2)), null);
		chat.insertRequest(mocha, me, null, "PENDING", now.minus(Duration.ofHours(1)), null);
		// 팝업의 수에 들지 않는 요청: 거절한 요청, 내가 보낸 요청.
		chat.insertRequest(third, me, null, "REJECTED", now.minus(Duration.ofMinutes(1)), null);
		chat.insertRequest(me, third, null, "PENDING", now.minus(Duration.ofMinutes(1)), null);

		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.chatRequestCount").value(2))
				.andExpect(jsonPath("$.data.latestChatRequester.id").value(mocha))
				.andExpect(jsonPath("$.data.latestChatRequester.nickname").value("모카"));
	}

	@Test
	void 요청이_없으면_수는_0이고_가장_최근_요청자는_null이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.chatRequestCount").value(0))
				.andExpect(jsonPath("$.data.latestChatRequester").value(nullValue()));
	}

	@Test
	void 팝업의_수는_만료와_수락_거절_취소를_바로_따라간다() throws Exception {
		long expiring = chat.insertRequest(night, me, null, "PENDING", now, null);
		long toAccept = chat.insertRequest(mocha, me, null, "PENDING", now.plus(Duration.ofSeconds(1)), null);
		long toReject = chat.insertRequest(third, me, null, "PENDING", now.plus(Duration.ofSeconds(2)), null);
		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(jsonPath("$.data.chatRequestCount").value(3))
				.andExpect(jsonPath("$.data.latestChatRequester.id").value(third));

		mockMvc.perform(post("/api/v1/chat-requests/{id}/reject", toReject).cookie(session)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(jsonPath("$.data.chatRequestCount").value(2))
				.andExpect(jsonPath("$.data.latestChatRequester.id").value(mocha));

		mockMvc.perform(post("/api/v1/chat-requests/{id}/accept", toAccept).cookie(session)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(jsonPath("$.data.chatRequestCount").value(1))
				.andExpect(jsonPath("$.data.latestChatRequester.id").value(night));

		// 마지막 요청도 정확히 5일이 되면 세지 않는다.
		clock.setNow(now.plus(Duration.ofDays(5)));
		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(session))
				.andExpect(jsonPath("$.data.chatRequestCount").value(0))
				.andExpect(jsonPath("$.data.latestChatRequester").value(nullValue()));
		assertThat(chat.requestStatus(expiring)).isEqualTo("PENDING");
	}

}
