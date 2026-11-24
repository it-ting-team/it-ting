package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화 요청 보내기: {@code POST /api/v1/chat-requests}. 내가 거절한 요청이 남아 있는 사람에게 보내면 대화방이 바로 열린다.
 * 수락 · 거절 · 취소는 {@link ChatRequestAnswerApiTests}, 목록 · 수는 {@link ChatRequestQueryApiTests}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatRequestSendApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

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

	long other;

	long third;

	Cookie session;

	Cookie otherSession;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		me = steps.createMember("me@example.com", "나두더지");
		other = steps.createMember("other@example.com", "두더지왕");
		third = steps.createMember("third@example.com", "새벽산책");
		session = steps.login(mockMvc, "me@example.com");
		otherSession = steps.login(mockMvc, "other@example.com");
	}

	// ---- 새 요청 ----

	@Test
	void 요청을_보내면_201과_요청_id_보낸_시각_만료_시각을_주고_받은_사람에게_알림이_간다() throws Exception {
		String response = send(session, other, "저도 러닝 좋아해요. 같이 달려요!")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.chatRoomId").value((Object) null))
				.andExpect(jsonPath("$.error").value((Object) null))
				.andReturn().getResponse().getContentAsString();

		long chatRequestId = ((Number) JsonPath.read(response, "$.data.chatRequestId")).longValue();
		Instant createdAt = Instant.parse(JsonPath.read(response, "$.data.createdAt"));
		Instant expiresAt = Instant.parse(JsonPath.read(response, "$.data.expiresAt"));
		assertThat(createdAt).isEqualTo(now.truncatedTo(ChronoUnit.SECONDS));
		assertThat(expiresAt).isEqualTo(createdAt.plus(Duration.ofDays(5)));

		Map<String, Object> row = chat.request(chatRequestId);
		assertThat(row)
				.containsEntry("status", "PENDING")
				.containsEntry("message", "저도 러닝 좋아해요. 같이 달려요!");
		assertThat(((Number) row.get("sender_id")).longValue()).isEqualTo(me);
		assertThat(((Number) row.get("receiver_id")).longValue()).isEqualTo(other);
		assertThat(row.get("chat_room_id")).isNull();
		assertThat(row.get("responded_at")).isNull();
		assertThat(chat.roomCount()).isZero();

		List<Map<String, Object>> notifications = chat.notifications(other);
		assertThat(notifications).hasSize(1);
		assertThat(notifications.getFirst())
				.containsEntry("type", "CHAT_REQUEST_RECEIVED")
				.containsEntry("target_type", "CHAT_REQUEST");
		assertThat(((Number) notifications.getFirst().get("target_id")).longValue()).isEqualTo(chatRequestId);
		assertThat(((Number) notifications.getFirst().get("actor_id")).longValue()).isEqualTo(me);
		assertThat(chat.notifications(me)).isEmpty();
	}

	@Test
	void 인사를_보내지_않거나_null이거나_빈_문자열이면_인사_없이_보낸다() throws Exception {
		mockMvc.perform(post("/api/v1/chat-requests").cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ \"receiverId\": %d }".formatted(other)))
				.andExpect(status().isCreated());
		send(session, third, null).andExpect(status().isCreated());
		long fourth = steps.createMember("fourth@example.com", "모카");
		send(session, fourth, "").andExpect(status().isCreated());

		for (long receiver : List.of(other, third, fourth)) {
			Map<String, Object> row = chat.requestsBetween(me, receiver).getFirst();
			assertThat(row.get("message")).as("받은 사람 %d의 인사", receiver).isNull();
			assertThat(row).containsEntry("status", "PENDING");
		}
	}

	@Test
	void 인사는_50자까지_받고_글자_수는_코드_포인트로_센다() throws Exception {
		String fifty = "🐹".repeat(50);

		send(session, other, fifty).andExpect(status().isCreated());

		assertThat(chat.requestsBetween(me, other).getFirst()).containsEntry("message", fifty);
	}

	@Test
	void 인사가_51자면_400_INVALID_INPUT이고_요청을_만들지_않는다() throws Exception {
		send(session, other, "가".repeat(51))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("message"));
		send(session, other, "🐹".repeat(51))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("message"));

		assertThat(chat.requestsBetween(me, other)).isEmpty();
		assertThat(chat.notifications(other)).isEmpty();
	}

	@Test
	void 나_자신에게_보내면_400_INVALID_INPUT이다() throws Exception {
		send(session, me, "혼잣말")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("receiverId"));

		assertThat(chat.requestsBetween(me, me)).isEmpty();
	}

	@Test
	void 받을_사람을_보내지_않거나_숫자가_아니면_400이다() throws Exception {
		mockMvc.perform(post("/api/v1/chat-requests").cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ \"message\": \"안녕하세요\" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("receiverId"));
		mockMvc.perform(post("/api/v1/chat-requests").cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ \"receiverId\": \"두더지\" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("receiverId"));
	}

	@Test
	void 없는_사람_탈퇴한_사람_온보딩을_마치지_않은_사람에게는_404_USER_NOT_FOUND다() throws Exception {
		long withdrawn = steps.createMember("withdrawn@example.com", "떠난두더지");
		testUsers.withdraw(withdrawn);
		long notOnboarded = testUsers.createEmailUser("fresh@example.com", "mole1234!");

		for (long receiver : List.of(9_999_999L, withdrawn, notOnboarded)) {
			send(session, receiver, "안녕하세요")
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
		}

		assertThat(chat.requestsBetween(me, withdrawn)).isEmpty();
		assertThat(chat.requestsBetween(me, notOnboarded)).isEmpty();
	}

	// ---- 보낼 수 없을 때 ----

	@Test
	void 이미_보낸_요청이_살아_있으면_409_CHAT_REQUEST_ALREADY_SENT다() throws Exception {
		send(session, other, "첫 요청").andExpect(status().isCreated());

		send(session, other, "다시")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_ALREADY_SENT"));

		assertThat(chat.requestsBetween(me, other)).hasSize(1);
		assertThat(chat.notifications(other)).hasSize(1);
	}

	@Test
	void 상대가_거절한_요청도_보낸_사람에게는_살아_있어서_다시_보낼_수_없다() throws Exception {
		long chatRequestId = chat.insertRequest(me, other, null, "REJECTED", now.minus(Duration.ofDays(1)), null);

		send(session, other, "다시")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_ALREADY_SENT"));

		assertThat(chat.requestStatus(chatRequestId)).isEqualTo("REJECTED");
		assertThat(chat.requestsBetween(me, other)).hasSize(1);
	}

	@Test
	void 상대가_보낸_요청에_답하지_않았으면_409_CHAT_REQUEST_ALREADY_RECEIVED다() throws Exception {
		send(otherSession, me, "먼저 보냈어요").andExpect(status().isCreated());

		send(session, other, "저도요")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_ALREADY_RECEIVED"));

		assertThat(chat.requestsBetween(me, other)).hasSize(1);
		assertThat(chat.roomCount()).isZero();
		assertThat(chat.notifications(other)).isEmpty();
	}

	@Test
	void 이미_대화_중이면_어느_쪽에서_보내도_409_CHAT_ROOM_ALREADY_EXISTS다() throws Exception {
		long roomId = chat.insertDirectRoom(me, other, now.minus(Duration.ofDays(10)));

		send(session, other, "안녕하세요")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_ALREADY_EXISTS"));
		send(otherSession, me, "안녕하세요")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_ALREADY_EXISTS"));

		assertThat(chat.requestsBetween(me, other)).isEmpty();
		assertThat(chat.members(roomId)).hasSize(2);
	}

	@Test
	void 한_사람이_나간_대화방은_열린_방이_아니라서_다시_요청할_수_있다() throws Exception {
		long roomId = chat.insertDirectRoom(me, other, now.minus(Duration.ofDays(10)));
		chat.leave(roomId, other);

		send(session, other, "다시 이야기해요")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.chatRequestId").isNumber())
				.andExpect(jsonPath("$.data.chatRoomId").value((Object) null));

		assertThat(chat.requestsBetween(me, other)).hasSize(1);
		// 읽기 전용이 된 예전 방은 그대로 남는다.
		assertThat(chat.roomCount()).isEqualTo(1);
	}

	// ---- 취소 · 만료 뒤 ----

	@Test
	void 취소한_뒤에는_바로_다시_보낼_수_있다() throws Exception {
		String response = send(session, other, "첫 요청").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long first = ((Number) JsonPath.read(response, "$.data.chatRequestId")).longValue();
		mockMvc.perform(post("/api/v1/chat-requests/{id}/cancel", first).cookie(session))
				.andExpect(status().isOk());

		String again = send(session, other, "다시 보내요").andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		long second = ((Number) JsonPath.read(again, "$.data.chatRequestId")).longValue();
		assertThat(second).isNotEqualTo(first);
		assertThat(chat.requestStatus(first)).isEqualTo("CANCELED");
		assertThat(chat.requestStatus(second)).isEqualTo("PENDING");
	}

	@Test
	void 보낸_지_5일이_되기_직전까지는_막고_정확히_5일이_되면_다시_보낼_수_있다() throws Exception {
		send(session, other, "첫 요청").andExpect(status().isCreated());

		clock.setNow(now.plus(Duration.ofDays(5)).minus(Duration.ofNanos(1_000)));
		send(session, other, "아직이에요")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_ALREADY_SENT"));

		clock.setNow(now.plus(Duration.ofDays(5)));
		send(session, other, "이제 새로").andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.chatRequestId").isNumber());

		assertThat(chat.requestsBetween(me, other)).hasSize(2);
	}

	@Test
	void 상대가_보낸_요청도_정확히_5일이_되면_죽어서_새_요청을_보낼_수_있다() throws Exception {
		send(otherSession, me, "먼저 보냈어요").andExpect(status().isCreated());

		clock.setNow(now.plus(Duration.ofDays(5)).minus(Duration.ofNanos(1_000)));
		send(session, other, "저도요")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_ALREADY_RECEIVED"));

		clock.setNow(now.plus(Duration.ofDays(5)));
		send(session, other, "저도요").andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.chatRoomId").value((Object) null));

		assertThat(chat.requestsBetween(me, other)).hasSize(2);
		assertThat(chat.roomCount()).isZero();
	}

	// ---- 내가 거절한 요청이 남아 있는 사람에게 보내기 ----

	@Test
	void 내가_거절한_사람에게_보내면_새_요청_없이_그_요청을_수락한_것으로_처리하고_인사가_첫_메시지가_된다() throws Exception {
		// other가 me에게 보냈고 me가 거절했다. me가 other에게 요청을 보낸다.
		long incoming = chat.insertRequest(other, me, "other의 인사", "REJECTED", now.minus(Duration.ofDays(1)), null);

		String response = send(session, other, "거절했지만 생각이 바뀌었어요")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.chatRequestId").value((Object) null))
				.andExpect(jsonPath("$.data.createdAt").value((Object) null))
				.andExpect(jsonPath("$.data.expiresAt").value((Object) null))
				.andReturn().getResponse().getContentAsString();

		long roomId = ((Number) JsonPath.read(response, "$.data.chatRoomId")).longValue();
		// 새 요청은 없고, other의 요청이 수락됐다.
		assertThat(chat.requestsBetween(me, other)).hasSize(1);
		Map<String, Object> request = chat.request(incoming);
		assertThat(request).containsEntry("status", "ACCEPTED");
		assertThat(((Number) request.get("chat_room_id")).longValue()).isEqualTo(roomId);
		assertThat(request.get("responded_at")).isNotNull();
		// 대화방과 두 사람의 참여자.
		assertThat(chat.room(roomId)).containsEntry("type", "DIRECT");
		assertThat(chat.room(roomId).get("meetup_id")).isNull();
		List<Map<String, Object>> members = chat.members(roomId);
		assertThat(members).extracting(member -> ((Number) member.get("user_id")).longValue())
				.containsExactlyInAnyOrder(me, other);
		assertThat(members).allSatisfy(member -> {
			assertThat(member.get("joined_at")).isNotNull();
			assertThat(member.get("left_at")).isNull();
		});
		// 안내 메시지(수락한 사람은 me) 다음에 me가 보낸 인사가 첫 메시지다. 상대의 인사는 옮기지 않는다.
		List<Map<String, Object>> messages = chat.messages(roomId);
		assertThat(messages).hasSize(2);
		assertThat(messages.get(0)).containsEntry("type", "ACCEPTED");
		assertThat(((Number) messages.get(0).get("sender_id")).longValue()).isEqualTo(me);
		assertThat(messages.get(0).get("content")).isNull();
		assertThat(messages.get(1)).containsEntry("type", "TEXT").containsEntry("content", "거절했지만 생각이 바뀌었어요");
		assertThat(((Number) messages.get(1).get("sender_id")).longValue()).isEqualTo(me);
		// 보낸 사람(me)의 읽은 자리는 그 메시지다. 상대는 읽지 않았다.
		long greetingId = ((Number) messages.get(1).get("id")).longValue();
		Map<Long, Object> lastRead = new java.util.HashMap<>();
		members.forEach(member -> lastRead.put(((Number) member.get("user_id")).longValue(),
				member.get("last_read_message_id")));
		assertThat(((Number) lastRead.get(me)).longValue()).isEqualTo(greetingId);
		assertThat(lastRead.get(other)).isNull();
		// other(요청을 처음 보낸 사람)에게 수락 알림과 새 메시지 알림이 간다. me에게는 아무것도 없다.
		List<Map<String, Object>> notifications = chat.notifications(other);
		assertThat(notifications).extracting(row -> row.get("type"))
				.containsExactly("CHAT_REQUEST_ACCEPTED", "CHAT_MESSAGE_RECEIVED");
		assertThat(notifications).allSatisfy(row -> {
			assertThat(row).containsEntry("target_type", "CHAT_ROOM");
			assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(roomId);
			assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(me);
		});
		assertThat(chat.notifications(me)).isEmpty();
	}

	@Test
	void 내가_거절한_사람에게_인사_없이_보내면_수락_안내_메시지와_수락_알림만_간다() throws Exception {
		long incoming = chat.insertRequest(other, me, null, "REJECTED", now.minus(Duration.ofHours(3)), null);

		String response = send(session, other, null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.chatRequestId").value((Object) null))
				.andReturn().getResponse().getContentAsString();

		long roomId = ((Number) JsonPath.read(response, "$.data.chatRoomId")).longValue();
		assertThat(chat.requestStatus(incoming)).isEqualTo("ACCEPTED");
		List<Map<String, Object>> messages = chat.messages(roomId);
		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst()).containsEntry("type", "ACCEPTED");
		assertThat(chat.members(roomId)).allSatisfy(member -> assertThat(member.get("last_read_message_id")).isNull());
		assertThat(chat.notificationTypes(other)).containsExactly("CHAT_REQUEST_ACCEPTED");
		assertThat(chat.notifications(me)).isEmpty();
	}

	@Test
	void 거절한_요청이_정확히_5일이_지났으면_수락하지_않고_새_요청을_만든다() throws Exception {
		long incoming = chat.insertRequest(other, me, null, "REJECTED", now.minus(Duration.ofDays(5)), null);

		send(session, other, "새로 보내요")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.chatRequestId").isNumber())
				.andExpect(jsonPath("$.data.chatRoomId").value((Object) null));

		assertThat(chat.requestStatus(incoming)).isEqualTo("REJECTED");
		assertThat(chat.requestsBetween(me, other)).hasSize(2);
		assertThat(chat.roomCount()).isZero();
	}

	// ---- 알림 · 로그인 · 계정 상태 ----

	@Test
	void 받는_사람이_채팅_알림을_꺼_두었으면_요청은_가고_알림은_만들지_않는다() throws Exception {
		jdbcTemplate.update("""
				INSERT INTO notification_setting (user_id, meetup_on, chat_on, community_on, burrow_on, notice_on,
				                                  created_at, updated_at)
				VALUES (?, TRUE, FALSE, TRUE, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", other);
		steps.flush();

		send(session, other, "안녕하세요").andExpect(status().isCreated());

		assertThat(chat.requestsBetween(me, other)).hasSize(1);
		assertThat(chat.notifications(other)).isEmpty();
	}

	@Test
	void 로그인하지_않으면_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(post("/api/v1/chat-requests")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ \"receiverId\": %d }".formatted(other)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		assertThat(chat.requestsBetween(me, other)).isEmpty();
	}

	@Test
	void 요청한_시각은_UTC로_저장된다() throws Exception {
		send(session, other, "안녕하세요").andExpect(status().isCreated());

		LocalDateTime createdAt = jdbcTemplate.queryForObject(
				"SELECT created_at FROM chat_request WHERE sender_id = ? AND receiver_id = ?", LocalDateTime.class,
				me, other);
		assertThat(createdAt.toInstant(ZoneOffset.UTC)).isEqualTo(now);
	}

	private ResultActions send(Cookie cookie, long receiverId, String message) throws Exception {
		String body = message == null ? "{ \"receiverId\": %d, \"message\": null }".formatted(receiverId)
				: "{ \"receiverId\": %d, \"message\": \"%s\" }".formatted(receiverId, message);
		return mockMvc.perform(post("/api/v1/chat-requests").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

}
