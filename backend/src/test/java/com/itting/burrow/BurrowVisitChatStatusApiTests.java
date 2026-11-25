package com.itting.burrow;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.itting.chat.domain.ChatRequest;
import com.itting.support.ChatSteps;
import com.itting.support.Members;
import com.itting.support.Members.Member;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 남의 땅굴 조회의 대화 버튼 상태: {@code GET /api/v1/users/{userId}/burrow}의 {@code chat}.
 * {@code docs/api/05-explore-visit.md}의 "대화 버튼의 상태"를 위에서부터 차례로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class BurrowVisitChatStatusApiTests {

	private static final String VISIT = "/api/v1/users/{userId}/burrow";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TestUsers testUsers;

	@Autowired
	OnboardingSteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	MutableClock clock;

	Instant now;

	Member me;

	Member owner;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		steps.clearNicknameHolds();
		Members members = new Members(mockMvc, testUsers, steps, jdbc);
		me = members.onboard("chat-status-me@example.com", "방문두더지");
		owner = members.onboard("chat-status-owner@example.com", "주인두더지");
	}

	@Test
	void 아무_관계가_없으면_NONE이다() throws Exception {
		visitOwner().andExpect(jsonPath("$.data.chat.status").value("NONE"))
				.andExpect(jsonPath("$.data.chat.chatRoomId").value(nullValue()));
	}

	@Test
	void 내가_보낸_요청을_기다리는_중이면_REQUEST_SENT다() throws Exception {
		chat.insertRequest(me.userId(), owner.userId(), "안녕", "PENDING", now.minus(Duration.ofHours(1)), null);

		visitOwner().andExpect(jsonPath("$.data.chat.status").value("REQUEST_SENT"))
				.andExpect(jsonPath("$.data.chat.chatRoomId").value(nullValue()));
	}

	@Test
	void 내가_보낸_요청이_거절돼도_만료될_때까지_REQUEST_SENT다() throws Exception {
		chat.insertRequest(me.userId(), owner.userId(), null, "REJECTED", now.minus(Duration.ofDays(4)), null);

		visitOwner().andExpect(jsonPath("$.data.chat.status").value("REQUEST_SENT"));
	}

	@Test
	void 내가_보낸_요청이_5일_지나면_NONE이다() throws Exception {
		chat.insertRequest(me.userId(), owner.userId(), null, "PENDING",
				now.minus(ChatRequest.LIFETIME).minusSeconds(1), null);

		visitOwner().andExpect(jsonPath("$.data.chat.status").value("NONE"));
	}

	@Test
	void 상대가_보낸_요청에_답하지_않았으면_REQUEST_RECEIVED다() throws Exception {
		chat.insertRequest(owner.userId(), me.userId(), "같이 얘기해요", "PENDING", now.minus(Duration.ofDays(1)),
				null);

		visitOwner().andExpect(jsonPath("$.data.chat.status").value("REQUEST_RECEIVED"))
				.andExpect(jsonPath("$.data.chat.chatRoomId").value(nullValue()));
	}

	@Test
	void 상대가_보낸_요청이_5일_지나면_NONE이다() throws Exception {
		chat.insertRequest(owner.userId(), me.userId(), null, "PENDING",
				now.minus(ChatRequest.LIFETIME).minusSeconds(1), null);

		visitOwner().andExpect(jsonPath("$.data.chat.status").value("NONE"));
	}

	@Test
	void 내가_거절한_요청이_남아_있으면_NONE이다() throws Exception {
		chat.insertRequest(owner.userId(), me.userId(), null, "REJECTED", now.minus(Duration.ofDays(1)), null);

		visitOwner().andExpect(jsonPath("$.data.chat.status").value("NONE"));
	}

	@Test
	void 대화방이_열려_있으면_CHATTING과_대화방_id를_준다() throws Exception {
		long roomId = chat.insertDirectRoom(me.userId(), owner.userId(), now.minus(Duration.ofDays(1)));
		chat.insertRequest(owner.userId(), me.userId(), null, "ACCEPTED", now.minus(Duration.ofDays(2)), roomId);

		visitOwner().andExpect(jsonPath("$.data.chat.status").value("CHATTING"))
				.andExpect(jsonPath("$.data.chat.chatRoomId").value(roomId));
	}

	@Test
	void 대화방이_있으면_살아_있는_요청이_있어도_CHATTING이다() throws Exception {
		long roomId = chat.insertDirectRoom(me.userId(), owner.userId(), now.minus(Duration.ofDays(1)));
		chat.insertRequest(me.userId(), owner.userId(), null, "PENDING", now.minus(Duration.ofHours(1)), null);

		visitOwner().andExpect(jsonPath("$.data.chat.status").value("CHATTING"))
				.andExpect(jsonPath("$.data.chat.chatRoomId").value(roomId));
	}

	@Test
	void 한_사람이_나간_대화방은_보지_않고_요청으로_정한다() throws Exception {
		long roomId = chat.insertDirectRoom(me.userId(), owner.userId(), now.minus(Duration.ofDays(3)));
		chat.leave(roomId, owner.userId());
		chat.insertRequest(owner.userId(), me.userId(), null, "PENDING", now.minus(Duration.ofHours(1)), null);

		visitOwner().andExpect(jsonPath("$.data.chat.status").value("REQUEST_RECEIVED"))
				.andExpect(jsonPath("$.data.chat.chatRoomId").value(nullValue()));
	}

	@Test
	void 다른_사람과의_관계는_섞이지_않는다() throws Exception {
		long third = testUsers.createEmailUser("chat-status-third@example.com", Members.PASSWORD);
		chat.insertDirectRoom(me.userId(), third, now.minus(Duration.ofDays(1)));
		chat.insertRequest(third, owner.userId(), null, "PENDING", now.minus(Duration.ofHours(1)), null);

		visitOwner().andExpect(jsonPath("$.data.chat.status").value("NONE"));
	}

	@Test
	void 내_땅굴은_요청이_있어도_NONE이다() throws Exception {
		chat.insertRequest(owner.userId(), me.userId(), null, "PENDING", now.minus(Duration.ofHours(1)), null);

		mockMvc.perform(get(VISIT, me.userId()).cookie(me.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isMine").value(true))
				.andExpect(jsonPath("$.data.chat.status").value("NONE"))
				.andExpect(jsonPath("$.data.chat.chatRoomId").value(nullValue()));
	}

	@Test
	void 없는_사람의_땅굴은_404다() throws Exception {
		mockMvc.perform(get(VISIT, 999_999_999L).cookie(me.session()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("BURROW_NOT_FOUND"));
	}

	private ResultActions visitOwner() throws Exception {
		return mockMvc.perform(get(VISIT, owner.userId()).cookie(me.session())).andExpect(status().isOk());
	}

}
