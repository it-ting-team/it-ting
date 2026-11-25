package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.TestUsers;
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
 * 대화 요청 목록과 수: {@code GET /api/v1/me/chat-requests}, {@code GET /api/v1/me/chat-requests/count}.
 * 받은 요청에는 거절한 요청이 없고, 보낸 요청에는 거절된 요청도 {@code PENDING}과 똑같이 나온다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatRequestQueryApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	TestUsers testUsers;

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

	// ---- 받은 요청 ----

	@Test
	void 받은_요청은_새로_보낸_것부터_상대와_인사와_만료_시각을_준다() throws Exception {
		long older = chat.insertRequest(night, me, null, "PENDING", now.minus(Duration.ofDays(2)), null);
		long newer = chat.insertRequest(mocha, me, "보드게임 같이 해요!", "PENDING", now.minus(Duration.ofHours(1)), null);

		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "RECEIVED").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").value((Object) null))
				.andExpect(jsonPath("$.data.items", hasSize(2)))
				.andExpect(jsonPath("$.data.items[0].id").value(newer))
				.andExpect(jsonPath("$.data.items[0].counterpart.id").value(mocha))
				.andExpect(jsonPath("$.data.items[0].counterpart.nickname").value("모카"))
				.andExpect(jsonPath("$.data.items[0].counterpart.mole.head").value("CAP_RED"))
				.andExpect(jsonPath("$.data.items[0].counterpart.isWithdrawn").value(false))
				.andExpect(jsonPath("$.data.items[0].message").value("보드게임 같이 해요!"))
				.andExpect(jsonPath("$.data.items[0].sharedInterests", hasSize(0)))
				.andExpect(jsonPath("$.data.items[0].createdAt")
						.value(now.minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS).toString()))
				.andExpect(jsonPath("$.data.items[0].expiresAt").value(
						now.minus(Duration.ofHours(1)).plus(Duration.ofDays(5)).truncatedTo(ChronoUnit.SECONDS)
								.toString()))
				.andExpect(jsonPath("$.data.items[1].id").value(older))
				.andExpect(jsonPath("$.data.items[1].counterpart.id").value(night))
				.andExpect(jsonPath("$.data.items[1].message").value((Object) null));
	}

	@Test
	void 받은_요청에는_거절한_요청_취소한_요청_수락한_요청_내가_보낸_요청_만료된_요청이_없다() throws Exception {
		long alive = chat.insertRequest(mocha, me, null, "PENDING", now.minus(Duration.ofDays(1)), null);
		long room = chat.insertDirectRoom(me, night, now.minus(Duration.ofDays(1)));
		chat.insertRequest(night, me, null, "ACCEPTED", now.minus(Duration.ofDays(2)), room);
		chat.insertRequest(third, me, null, "REJECTED", now.minus(Duration.ofDays(1)), null);
		chat.insertRequest(third, me, null, "CANCELED", now.minus(Duration.ofDays(1)), null);
		chat.insertRequest(me, third, null, "PENDING", now.minus(Duration.ofDays(1)), null);
		long expiredSender = steps.createMember("old@example.com", "오래전");
		chat.insertRequest(expiredSender, me, null, "PENDING", now.minus(Duration.ofDays(5)), null);

		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "RECEIVED").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(alive));
	}

	@Test
	void 받은_요청은_보낸_지_5일이_되기_직전까지_보이고_정확히_5일이_되면_사라진다() throws Exception {
		chat.insertRequest(mocha, me, null, "PENDING", now, null);

		clock.setNow(now.plus(Duration.ofDays(5)).minus(Duration.ofNanos(1_000)));
		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "RECEIVED").cookie(session))
				.andExpect(jsonPath("$.data.items", hasSize(1)));
		mockMvc.perform(get("/api/v1/me/chat-requests/count").cookie(session))
				.andExpect(jsonPath("$.data.receivedCount").value(1));

		clock.setNow(now.plus(Duration.ofDays(5)));
		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "RECEIVED").cookie(session))
				.andExpect(jsonPath("$.data.items", hasSize(0)));
		mockMvc.perform(get("/api/v1/me/chat-requests/count").cookie(session))
				.andExpect(jsonPath("$.data.receivedCount").value(0));
	}

	@Test
	void 겹치는_관심사_태그는_겹치는_것만_상대가_고른_순서로_싣는다() throws Exception {
		long running = steps.firstInterestOf(1);
		long board = steps.firstInterestOf(2);
		long movie = steps.firstInterestOf(3);
		long music = steps.firstInterestOf(4);
		steps.setInterests(me, List.of(running, board, movie));
		// 모카는 movie, music, running 순서로 골랐다. 나와 겹치는 것은 movie, running이고 순서는 모카의 순서다.
		steps.setInterests(mocha, List.of(movie, music, running));
		steps.setInterests(night, List.of(music));
		chat.insertRequest(mocha, me, null, "PENDING", now.minus(Duration.ofHours(2)), null);
		chat.insertRequest(night, me, null, "PENDING", now.minus(Duration.ofHours(3)), null);
		chat.insertRequest(third, me, null, "PENDING", now.minus(Duration.ofHours(4)), null);
		steps.flush();

		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "RECEIVED").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].counterpart.id").value(mocha))
				.andExpect(jsonPath("$.data.items[0].sharedInterests", hasSize(2)))
				.andExpect(jsonPath("$.data.items[0].sharedInterests[0].id").value(movie))
				.andExpect(jsonPath("$.data.items[0].sharedInterests[0].interestCategoryId").value(3))
				.andExpect(jsonPath("$.data.items[0].sharedInterests[0].isShared").value(true))
				.andExpect(jsonPath("$.data.items[0].sharedInterests[1].id").value(running))
				.andExpect(jsonPath("$.data.items[1].counterpart.id").value(night))
				.andExpect(jsonPath("$.data.items[1].sharedInterests", hasSize(0)))
				.andExpect(jsonPath("$.data.items[2].counterpart.id").value(third))
				.andExpect(jsonPath("$.data.items[2].sharedInterests", hasSize(0)));
	}

	// ---- 보낸 요청 ----

	@Test
	void 보낸_요청은_PENDING과_REJECTED를_구분_없이_새것부터_주고_거절됐는지_알려_주는_값이_없다() throws Exception {
		long first = chat.insertRequest(me, mocha, "첫 요청", "PENDING", now.minus(Duration.ofDays(2)), null);
		long second = chat.insertRequest(me, night, null, "REJECTED", now.minus(Duration.ofDays(1)), null);

		String body = mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "SENT").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(2)))
				.andExpect(jsonPath("$.data.items[0].id").value(second))
				.andExpect(jsonPath("$.data.items[0].counterpart.id").value(night))
				.andExpect(jsonPath("$.data.items[0].message").value((Object) null))
				.andExpect(jsonPath("$.data.items[1].id").value(first))
				.andExpect(jsonPath("$.data.items[1].counterpart.id").value(mocha))
				.andExpect(jsonPath("$.data.items[1].message").value("첫 요청"))
				.andReturn().getResponse().getContentAsString();

		// 거절을 드러내는 어떤 값도 응답에 없다.
		assertThat(body).doesNotContain("REJECTED", "status", "rejected");
	}

	@Test
	void 보낸_요청에는_취소한_요청_수락된_요청_받은_요청_만료된_요청이_없다() throws Exception {
		long alive = chat.insertRequest(me, mocha, null, "PENDING", now.minus(Duration.ofDays(1)), null);
		long room = chat.insertDirectRoom(me, night, now.minus(Duration.ofDays(1)));
		chat.insertRequest(me, night, null, "ACCEPTED", now.minus(Duration.ofDays(2)), room);
		chat.insertRequest(me, third, null, "CANCELED", now.minus(Duration.ofDays(1)), null);
		chat.insertRequest(third, me, null, "PENDING", now.minus(Duration.ofDays(1)), null);
		long old = steps.createMember("old@example.com", "오래전");
		chat.insertRequest(me, old, null, "REJECTED", now.minus(Duration.ofDays(5)), null);

		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "SENT").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].id").value(alive));
	}

	@Test
	void 요청이_없으면_빈_목록이고_다른_사람의_요청은_나오지_않는다() throws Exception {
		chat.insertRequest(mocha, night, null, "PENDING", now.minus(Duration.ofHours(1)), null);

		for (String direction : List.of("RECEIVED", "SENT")) {
			mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", direction).cookie(session))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items", hasSize(0)));
		}
	}

	// ---- 입력 · 로그인 ----

	@Test
	void direction이_없거나_목록에_없는_값이면_400_INVALID_INPUT이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/chat-requests").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "ALL").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 로그인하지_않으면_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "RECEIVED"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/v1/me/chat-requests/count"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	// ---- 수 ----

	@Test
	void 수는_받은_요청은_PENDING만_보낸_요청은_PENDING과_REJECTED를_센다() throws Exception {
		chat.insertRequest(mocha, me, null, "PENDING", now.minus(Duration.ofDays(1)), null);
		chat.insertRequest(night, me, null, "PENDING", now.minus(Duration.ofDays(1)), null);
		chat.insertRequest(third, me, null, "REJECTED", now.minus(Duration.ofDays(1)), null);
		chat.insertRequest(me, mocha, null, "PENDING", now.minus(Duration.ofDays(1)), null);
		chat.insertRequest(me, night, null, "REJECTED", now.minus(Duration.ofDays(1)), null);
		chat.insertRequest(me, third, null, "CANCELED", now.minus(Duration.ofDays(1)), null);

		mockMvc.perform(get("/api/v1/me/chat-requests/count").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.receivedCount").value(2))
				.andExpect(jsonPath("$.data.sentCount").value(2));
	}

	@Test
	void 요청이_없으면_수는_0이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/chat-requests/count").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.receivedCount").value(0))
				.andExpect(jsonPath("$.data.sentCount").value(0));
	}

	@Test
	void 수는_목록의_개수와_늘_같다() throws Exception {
		chat.insertRequest(mocha, me, null, "PENDING", now.minus(Duration.ofDays(4)), null);
		chat.insertRequest(night, me, null, "PENDING", now.minus(Duration.ofDays(5)), null);
		chat.insertRequest(third, me, null, "REJECTED", now.minus(Duration.ofDays(1)), null);
		chat.insertRequest(me, mocha, null, "REJECTED", now.minus(Duration.ofDays(4)), null);
		chat.insertRequest(me, night, null, "PENDING", now.minus(Duration.ofDays(5)), null);
		chat.insertRequest(me, third, null, "PENDING", now.minus(Duration.ofHours(1)), null);

		mockMvc.perform(get("/api/v1/me/chat-requests/count").cookie(session))
				.andExpect(jsonPath("$.data.receivedCount").value(1))
				.andExpect(jsonPath("$.data.sentCount").value(2));
		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "RECEIVED").cookie(session))
				.andExpect(jsonPath("$.data.items", hasSize(1)));
		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "SENT").cookie(session))
				.andExpect(jsonPath("$.data.items", hasSize(2)));
	}

	@Test
	void 온보딩을_마치지_않은_계정은_403_ONBOARDING_INCOMPLETE다() throws Exception {
		testUsers.createEmailUser("fresh@example.com", CommunitySteps.PASSWORD);
		Cookie fresh = steps.login(mockMvc, "fresh@example.com");

		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "RECEIVED").cookie(fresh))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
		mockMvc.perform(get("/api/v1/me/chat-requests/count").cookie(fresh))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

}
