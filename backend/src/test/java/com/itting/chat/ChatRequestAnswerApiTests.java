package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화 요청 수락 · 거절 · 취소: {@code POST /api/v1/chat-requests/{chatRequestId}/accept},
 * {@code .../reject}, {@code .../cancel}. 거절은 보낸 사람에게 드러나지 않고, 취소는 거절된 요청에도 똑같이 성공한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatRequestAnswerApiTests {

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

	long sender;

	long stranger;

	Cookie session;

	Cookie senderSession;

	Cookie strangerSession;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		me = steps.createMember("me@example.com", "두더지왕");
		sender = steps.createMember("sender@example.com", "모카");
		stranger = steps.createMember("stranger@example.com", "새벽산책");
		session = steps.login(mockMvc, "me@example.com");
		senderSession = steps.login(mockMvc, "sender@example.com");
		strangerSession = steps.login(mockMvc, "stranger@example.com");
	}

	// ---- 수락 ----

	@Test
	void 수락하면_대화방과_두_사람의_참여자_줄과_안내_메시지가_생기고_요청이_ACCEPTED가_된다() throws Exception {
		long request = chat.insertRequest(sender, me, "보드게임 같이 해요!", "PENDING", now.minus(Duration.ofHours(2)), null);

		String response = accept(session, request)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").value((Object) null))
				.andReturn().getResponse().getContentAsString();

		long roomId = ((Number) JsonPath.read(response, "$.data.chatRoomId")).longValue();
		Map<String, Object> row = chat.request(request);
		assertThat(row).containsEntry("status", "ACCEPTED");
		assertThat(((Number) row.get("chat_room_id")).longValue()).isEqualTo(roomId);
		assertThat(row.get("responded_at")).isNotNull();

		assertThat(chat.room(roomId)).containsEntry("type", "DIRECT");
		List<Map<String, Object>> members = chat.members(roomId);
		assertThat(members).extracting(member -> ((Number) member.get("user_id")).longValue())
				.containsExactlyInAnyOrder(me, sender);
		assertThat(members).allSatisfy(member -> {
			assertThat(member.get("joined_at")).isNotNull();
			assertThat(member.get("left_at")).isNull();
			assertThat(member.get("last_read_message_id")).isNull();
		});
		// 요청에 붙인 인사는 옮기지 않는다. 대화 시작 안내만 있다. 안내의 sender는 수락한 사람이다.
		List<Map<String, Object>> messages = chat.messages(roomId);
		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst()).containsEntry("type", "ACCEPTED");
		assertThat(((Number) messages.getFirst().get("sender_id")).longValue()).isEqualTo(me);
		assertThat(messages.getFirst().get("content")).isNull();
	}

	@Test
	void 수락하면_보낸_사람에게만_수락_알림이_가고_알림의_대상은_대화방이다() throws Exception {
		long request = chat.insertRequest(sender, me, null, "PENDING", now.minus(Duration.ofHours(2)), null);

		String response = accept(session, request).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		long roomId = ((Number) JsonPath.read(response, "$.data.chatRoomId")).longValue();
		List<Map<String, Object>> notifications = chat.notifications(sender);
		assertThat(notifications).hasSize(1);
		assertThat(notifications.getFirst())
				.containsEntry("type", "CHAT_REQUEST_ACCEPTED")
				.containsEntry("target_type", "CHAT_ROOM");
		assertThat(((Number) notifications.getFirst().get("target_id")).longValue()).isEqualTo(roomId);
		assertThat(((Number) notifications.getFirst().get("actor_id")).longValue()).isEqualTo(me);
		assertThat(chat.notifications(me)).isEmpty();
	}

	@Test
	void 이미_수락한_요청을_다시_수락하면_그때_만든_대화방의_id를_주고_아무것도_더_만들지_않는다() throws Exception {
		long request = chat.insertRequest(sender, me, null, "PENDING", now.minus(Duration.ofHours(2)), null);
		String first = accept(session, request).andReturn().getResponse().getContentAsString();
		long roomId = ((Number) JsonPath.read(first, "$.data.chatRoomId")).longValue();

		accept(session, request)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.chatRoomId").value(roomId));

		assertThat(chat.roomCount()).isEqualTo(1);
		assertThat(chat.messages(roomId)).hasSize(1);
		assertThat(chat.members(roomId)).hasSize(2);
		assertThat(chat.notifications(sender)).hasSize(1);
	}

	@Test
	void 수락은_보낸_지_5일이_되기_직전까지_되고_정확히_5일이_되면_404다() throws Exception {
		long request = chat.insertRequest(sender, me, null, "PENDING", now, null);

		clock.setNow(now.plus(Duration.ofDays(5)));
		accept(session, request)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_NOT_FOUND"));
		assertThat(chat.requestStatus(request)).isEqualTo("PENDING");
		assertThat(chat.roomCount()).isZero();

		clock.setNow(now.plus(Duration.ofDays(5)).minus(Duration.ofNanos(1_000)));
		accept(session, request).andExpect(status().isOk());
		assertThat(chat.requestStatus(request)).isEqualTo("ACCEPTED");
	}

	@Test
	void 수락할_수_없는_요청은_모두_404_CHAT_REQUEST_NOT_FOUND이고_아무것도_바꾸지_않는다() throws Exception {
		long canceled = chat.insertRequest(sender, me, null, "CANCELED", now.minus(Duration.ofHours(1)), null);
		long rejected = chat.insertRequest(stranger, me, null, "REJECTED", now.minus(Duration.ofHours(1)), null);
		long mine = chat.insertRequest(me, sender, null, "PENDING", now.minus(Duration.ofHours(1)), null);
		long others = chat.insertRequest(sender, stranger, null, "PENDING", now.minus(Duration.ofHours(1)), null);

		for (long chatRequestId : List.of(canceled, rejected, mine, others, 9_999_999L)) {
			accept(session, chatRequestId)
					.andExpect(status().isNotFound())
					.andExpect(content().json("""
							{
							  "success": false,
							  "data": null,
							  "error": { "code": "CHAT_REQUEST_NOT_FOUND", "message": "대화 요청을 찾을 수 없습니다.", "fields": null, "detail": null }
							}
							""", JsonCompareMode.STRICT));
		}

		assertThat(chat.requestStatus(canceled)).isEqualTo("CANCELED");
		assertThat(chat.requestStatus(rejected)).isEqualTo("REJECTED");
		assertThat(chat.requestStatus(mine)).isEqualTo("PENDING");
		assertThat(chat.requestStatus(others)).isEqualTo("PENDING");
		assertThat(chat.roomCount()).isZero();
		assertThat(chat.notifications(sender)).isEmpty();
	}

	@Test
	void 요청_id가_숫자가_아니면_400이고_로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(post("/api/v1/chat-requests/abc/accept").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(post("/api/v1/chat-requests/1/accept"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(post("/api/v1/chat-requests/1/reject"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/chat-requests/1/cancel"))
				.andExpect(status().isUnauthorized());
	}

	// ---- 거절 ----

	@Test
	void 거절하면_REJECTED가_되고_보낸_사람에게_알림이_가지_않는다() throws Exception {
		long request = chat.insertRequest(sender, me, "인사", "PENDING", now.minus(Duration.ofHours(1)), null);

		reject(session, request)
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT));

		Map<String, Object> row = chat.request(request);
		assertThat(row).containsEntry("status", "REJECTED");
		assertThat(row.get("responded_at")).isNotNull();
		assertThat(row.get("chat_room_id")).isNull();
		assertThat(chat.roomCount()).isZero();
		assertThat(chat.notifications(sender)).isEmpty();
		assertThat(chat.notifications(me)).isEmpty();
	}

	@Test
	void 거절한_요청은_받은_사람의_받은_요청에서_빠지고_보낸_사람의_보낸_요청에는_그대로_남는다() throws Exception {
		long request = chat.insertRequest(sender, me, null, "PENDING", now.minus(Duration.ofHours(1)), null);
		reject(session, request).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/me/chat-requests/count").cookie(session))
				.andExpect(jsonPath("$.data.receivedCount").value(0));
		mockMvc.perform(get("/api/v1/me/chat-requests/count").cookie(senderSession))
				.andExpect(jsonPath("$.data.sentCount").value(1));
		mockMvc.perform(get("/api/v1/me/chat-requests").param("direction", "SENT").cookie(senderSession))
				.andExpect(jsonPath("$.data.items[0].id").value(request));
	}

	@Test
	void 이미_거절한_요청을_다시_거절해도_200이고_바뀌는_것이_없다() throws Exception {
		long request = chat.insertRequest(sender, me, null, "PENDING", now.minus(Duration.ofHours(1)), null);
		reject(session, request).andExpect(status().isOk());
		Object respondedAt = chat.request(request).get("responded_at");

		reject(session, request).andExpect(status().isOk());

		assertThat(chat.requestStatus(request)).isEqualTo("REJECTED");
		assertThat(chat.request(request).get("responded_at")).isEqualTo(respondedAt);
	}

	@Test
	void 거절한_요청이_5일이_지나면_다시_거절해도_404다() throws Exception {
		long request = chat.insertRequest(sender, me, null, "REJECTED", now.minus(Duration.ofDays(5)), null);

		reject(session, request)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_NOT_FOUND"));
	}

	@Test
	void 거절할_수_없는_요청은_404이고_이미_수락한_요청도_거절할_수_없다() throws Exception {
		long room = chat.insertDirectRoom(me, sender, now.minus(Duration.ofDays(1)));
		long accepted = chat.insertRequest(sender, me, null, "ACCEPTED", now.minus(Duration.ofDays(1)), room);
		long canceled = chat.insertRequest(stranger, me, null, "CANCELED", now.minus(Duration.ofHours(1)), null);
		long mine = chat.insertRequest(me, stranger, null, "PENDING", now.minus(Duration.ofHours(1)), null);
		long expired = chat.insertRequest(stranger, me, null, "PENDING", now.minus(Duration.ofDays(6)), null);

		for (long chatRequestId : List.of(accepted, canceled, mine, expired, 9_999_999L)) {
			reject(session, chatRequestId)
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_NOT_FOUND"));
		}

		assertThat(chat.requestStatus(accepted)).isEqualTo("ACCEPTED");
		assertThat(chat.requestStatus(canceled)).isEqualTo("CANCELED");
		assertThat(chat.requestStatus(mine)).isEqualTo("PENDING");
		assertThat(chat.requestStatus(expired)).isEqualTo("PENDING");
	}

	@Test
	void 남의_요청은_거절할_수_없다() throws Exception {
		long request = chat.insertRequest(sender, me, null, "PENDING", now.minus(Duration.ofHours(1)), null);

		reject(strangerSession, request)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_NOT_FOUND"));
		// 보낸 사람도 자기가 보낸 요청은 거절할 수 없다.
		reject(senderSession, request)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_NOT_FOUND"));

		assertThat(chat.requestStatus(request)).isEqualTo("PENDING");
	}

	// ---- 취소 ----

	@Test
	void 보낸_요청을_취소하면_CANCELED가_되고_알림은_만들지_않는다() throws Exception {
		long request = chat.insertRequest(me, sender, "인사", "PENDING", now.minus(Duration.ofHours(1)), null);

		cancel(session, request)
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT));

		Map<String, Object> row = chat.request(request);
		assertThat(row).containsEntry("status", "CANCELED");
		assertThat(row.get("responded_at")).isNotNull();
		assertThat(chat.notifications(sender)).isEmpty();
		assertThat(chat.notifications(me)).isEmpty();
	}

	@Test
	void 취소한_요청은_받은_사람의_받은_요청과_보낸_사람의_보낸_요청에서_모두_빠진다() throws Exception {
		long request = chat.insertRequest(me, sender, null, "PENDING", now.minus(Duration.ofHours(1)), null);
		cancel(session, request).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/me/chat-requests/count").cookie(senderSession))
				.andExpect(jsonPath("$.data.receivedCount").value(0));
		mockMvc.perform(get("/api/v1/me/chat-requests/count").cookie(session))
				.andExpect(jsonPath("$.data.sentCount").value(0));
	}

	@Test
	void 받은_사람이_이미_거절한_요청도_PENDING일_때와_똑같이_취소되고_응답도_같다() throws Exception {
		long pending = chat.insertRequest(me, sender, null, "PENDING", now.minus(Duration.ofHours(1)), null);
		long rejected = chat.insertRequest(me, stranger, null, "REJECTED", now.minus(Duration.ofHours(1)), null);

		String pendingBody = cancel(session, pending).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String rejectedBody = cancel(session, rejected).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(rejectedBody).isEqualTo(pendingBody);
		assertThat(chat.requestStatus(pending)).isEqualTo("CANCELED");
		assertThat(chat.requestStatus(rejected)).isEqualTo("CANCELED");
	}

	@Test
	void 상대가_그사이_수락했으면_409_CHAT_REQUEST_ALREADY_ACCEPTED와_대화방_id를_준다() throws Exception {
		long room = chat.insertDirectRoom(me, sender, now.minus(Duration.ofMinutes(5)));
		long request = chat.insertRequest(me, sender, null, "ACCEPTED", now.minus(Duration.ofHours(1)), room);

		cancel(session, request)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_ALREADY_ACCEPTED"))
				.andExpect(jsonPath("$.error.detail.chatRoomId").value(room));

		assertThat(chat.requestStatus(request)).isEqualTo("ACCEPTED");
	}

	@Test
	void 취소할_수_없는_요청은_404다() throws Exception {
		long canceled = chat.insertRequest(me, sender, null, "CANCELED", now.minus(Duration.ofHours(1)), null);
		long expired = chat.insertRequest(me, stranger, null, "PENDING", now.minus(Duration.ofDays(5)), null);
		long received = chat.insertRequest(sender, me, null, "PENDING", now.minus(Duration.ofHours(1)), null);
		long others = chat.insertRequest(sender, stranger, null, "PENDING", now.minus(Duration.ofHours(1)), null);

		for (long chatRequestId : List.of(canceled, expired, received, others, 9_999_999L)) {
			cancel(session, chatRequestId)
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_NOT_FOUND"));
		}

		assertThat(chat.requestStatus(canceled)).isEqualTo("CANCELED");
		assertThat(chat.requestStatus(expired)).isEqualTo("PENDING");
		assertThat(chat.requestStatus(received)).isEqualTo("PENDING");
	}

	@Test
	void 취소_뒤에는_받은_사람이_수락할_수_없다() throws Exception {
		long request = chat.insertRequest(me, sender, null, "PENDING", now.minus(Duration.ofHours(1)), null);
		cancel(session, request).andExpect(status().isOk());

		accept(senderSession, request)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CHAT_REQUEST_NOT_FOUND"));

		assertThat(chat.roomCount()).isZero();
	}

	// ---- 온보딩 ----

	@Test
	void 온보딩을_마치지_않은_계정은_수락_거절_취소가_모두_403_ONBOARDING_INCOMPLETE다() throws Exception {
		testUsers.createEmailUser("fresh@example.com", CommunitySteps.PASSWORD);
		Cookie fresh = steps.login(mockMvc, "fresh@example.com");

		for (String action : List.of("accept", "reject", "cancel")) {
			mockMvc.perform(post("/api/v1/chat-requests/1/" + action).cookie(fresh))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
		}
	}

	private ResultActions accept(Cookie cookie, long chatRequestId) throws Exception {
		return mockMvc.perform(post("/api/v1/chat-requests/{id}/accept", chatRequestId).cookie(cookie));
	}

	private ResultActions reject(Cookie cookie, long chatRequestId) throws Exception {
		return mockMvc.perform(post("/api/v1/chat-requests/{id}/reject", chatRequestId).cookie(cookie));
	}

	private ResultActions cancel(Cookie cookie, long chatRequestId) throws Exception {
		return mockMvc.perform(post("/api/v1/chat-requests/{id}/cancel", chatRequestId).cookie(cookie));
	}

}
