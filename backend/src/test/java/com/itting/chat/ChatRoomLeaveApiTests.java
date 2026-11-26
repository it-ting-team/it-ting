package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화방에서 나가기: {@code POST /api/v1/chat-rooms/{chatRoomId}/leave}. 대화방에서만 나가고 모임의 참여는 바꾸지 않는다.
 * 1:1은 언제나, 모임은 취소됐거나 끝났을 때만 나갈 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatRoomLeaveApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MutableClock clock;

	Instant now;

	long me;

	long friend;

	long host;

	Cookie session;

	Cookie friendSession;

	Cookie hostSession;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		me = steps.createMember("me@example.com", "두더지왕");
		friend = steps.createMember("friend@example.com", "새벽산책");
		host = steps.createMember("host@example.com", "밤코딩");
		session = steps.login(mockMvc, "me@example.com");
		friendSession = steps.login(mockMvc, "friend@example.com");
		hostSession = steps.login(mockMvc, "host@example.com");
	}

	// ---- 1:1 ----

	@Test
	void 일대일_대화방에서_나가면_내_목록에서_사라지고_상대에게는_읽기_전용으로_남는다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));
		chat.text(room, friend, "안녕하세요", now.minus(Duration.ofMinutes(10)));

		leave(session, room)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value((Object) null))
				.andExpect(jsonPath("$.error").value((Object) null));

		assertThat(chat.leftAt(room, me)).isNotNull();
		assertThat(chat.leftAt(room, friend)).isNull();
		Map<String, Object> last = chat.messages(room).getLast();
		assertThat(last).containsEntry("type", "LEFT");
		assertThat(last.get("content")).isNull();
		assertThat(((Number) last.get("sender_id")).longValue()).isEqualTo(me);

		list(session).andExpect(jsonPath("$.data.items.length()").value(0));
		list(friendSession)
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].isReadOnly").value(true))
				.andExpect(jsonPath("$.data.items[0].lastMessage.type").value("LEFT"))
				.andExpect(jsonPath("$.data.items[0].lastMessage.sender.nickname").value("두더지왕"));
	}

	@Test
	void 나간_뒤에는_두_사람_모두_상대에게_대화_요청을_보낼_수_있다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));
		requestChat(session, friend)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_ALREADY_EXISTS"));

		leave(session, room).andExpect(status().isOk());

		requestChat(friendSession, me).andExpect(status().isCreated());
	}

	@Test
	void 나가면_그_대화방의_안_읽은_새_메시지_알림이_읽음으로_바뀐다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));
		long other = chat.insertDirectRoom(me, host, now.minus(Duration.ofHours(1)));
		send(friendSession, room, "안녕하세요");
		send(hostSession, other, "저도 안녕하세요");
		assertThat(unreadChatNotifications(me)).hasSize(2);

		leave(session, room).andExpect(status().isOk());

		List<Map<String, Object>> unread = unreadChatNotifications(me);
		assertThat(unread).hasSize(1);
		assertThat(((Number) unread.getFirst().get("target_id")).longValue()).isEqualTo(other);
	}

	@Test
	void 상대가_먼저_나간_읽기_전용_대화방에서도_나갈_수_있다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));
		chat.leave(room, friend);

		leave(session, room).andExpect(status().isOk());

		assertThat(chat.leftAt(room, me)).isNotNull();
	}

	// ---- 모임 ----

	@Test
	void 끝난_모임의_대화방에서는_참여자도_주최자도_나갈_수_있고_참여_기록은_그대로다() throws Exception {
		long meetup = steps.insertMeetup(host, "지난 모임", now.minus(Duration.ofHours(2)), 4, null);
		steps.apply(meetup, me, "CONFIRMED");
		long room = chat.insertMeetupRoom(meetup, host, now.minus(Duration.ofDays(2)));
		chat.join(room, me, now.minus(Duration.ofDays(1)));

		leave(session, room).andExpect(status().isOk());

		assertThat(chat.leftAt(room, me)).isNotNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT status FROM meetup_application WHERE meetup_id = ? AND user_id = ?", String.class, meetup, me))
				.isEqualTo("CONFIRMED");
		assertThat(chat.messages(room).getLast()).containsEntry("type", "LEFT");
		list(session).andExpect(jsonPath("$.data.items.length()").value(0));
		// 남은 사람(주최자)의 방은 읽기 전용이 되지 않고, 참여자 목록에서만 빠진다.
		chat.sync();
		mockMvc.perform(get("/api/v1/chat-rooms/{id}", room).cookie(hostSession))
				.andExpect(jsonPath("$.data.isReadOnly").value(false))
				.andExpect(jsonPath("$.data.members.length()").value(1))
				// 확정 인원은 참여 기록으로 세므로 그대로다.
				.andExpect(jsonPath("$.data.meetup.confirmedCount").value(2));

		leave(hostSession, room).andExpect(status().isOk());
		assertThat(chat.leftAt(room, host)).isNotNull();
	}

	@Test
	void 취소된_모임의_대화방에서는_시작_전이어도_나갈_수_있다() throws Exception {
		long meetup = steps.insertMeetup(host, "취소된 모임", now.plus(Duration.ofDays(3)), 4, null);
		steps.apply(meetup, me, "CONFIRMED");
		long room = chat.insertMeetupRoom(meetup, host, now.minus(Duration.ofDays(2)));
		chat.join(room, me, now.minus(Duration.ofDays(1)));
		steps.cancelMeetup(meetup);

		leave(hostSession, room).andExpect(status().isOk());
		leave(session, room).andExpect(status().isOk());

		assertThat(chat.leftAt(room, host)).isNotNull();
		assertThat(chat.leftAt(room, me)).isNotNull();
	}

	@Test
	void 취소되지도_끝나지도_않은_모임의_대화방에서는_나갈_수_없다() throws Exception {
		long meetup = steps.insertMeetup(host, "다가오는 모임", now.plus(Duration.ofDays(3)), 4, null);
		steps.apply(meetup, me, "CONFIRMED");
		long room = chat.insertMeetupRoom(meetup, host, now.minus(Duration.ofDays(2)));
		chat.join(room, me, now.minus(Duration.ofDays(1)));

		for (Cookie cookie : List.of(session, hostSession)) {
			leave(cookie, room)
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_LEAVE_NOT_ALLOWED"))
					.andExpect(jsonPath("$.error.message").value("끝나지 않은 모임의 대화방에서는 나갈 수 없습니다."));
		}

		assertThat(chat.leftAt(room, me)).isNull();
		assertThat(chat.leftAt(room, host)).isNull();
		assertThat(chat.messages(room)).isEmpty();
	}

	@Test
	void 모임의_참여를_취소해_대화방에서_빠지면_그_대화방의_새_메시지_알림이_읽음으로_바뀐다() throws Exception {
		long meetup = steps.insertMeetup(host, "다가오는 모임", now.plus(Duration.ofDays(3)), 4, null);
		steps.apply(meetup, me, "CONFIRMED");
		long room = chat.insertMeetupRoom(meetup, host, now.minus(Duration.ofDays(2)));
		chat.join(room, me, now.minus(Duration.ofDays(1)));
		send(hostSession, room, "토요일에 봐요");
		assertThat(unreadChatNotifications(me)).hasSize(1);

		chat.sync();
		mockMvc.perform(post("/api/v1/meetups/{id}/cancel-participation", meetup).cookie(session))
				.andExpect(status().isOk());

		assertThat(chat.leftAt(room, me)).isNotNull();
		assertThat(unreadChatNotifications(me)).isEmpty();
	}

	// ---- 들어갈 수 없는 대화방 ----

	@Test
	void 이미_나간_방과_남의_방과_없는_방은_404다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));
		long others = chat.insertDirectRoom(friend, host, now.minus(Duration.ofHours(1)));
		leave(session, room).andExpect(status().isOk());

		for (long target : List.of(room, others, 999_999_999L)) {
			leave(session, target)
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
		}
		// 두 번 불러도 안내 메시지는 하나다.
		assertThat(chat.messages(room)).hasSize(1);
		assertThat(chat.leftAt(others, friend)).isNull();

		mockMvc.perform(post("/api/v1/chat-rooms/{id}/leave", others)).andExpect(status().isUnauthorized());
	}

	private ResultActions leave(Cookie cookie, long roomId) throws Exception {
		chat.sync();
		ResultActions result = mockMvc.perform(post("/api/v1/chat-rooms/{id}/leave", roomId).cookie(cookie));
		chat.sync();
		return result;
	}

	private ResultActions list(Cookie cookie) throws Exception {
		chat.sync();
		return mockMvc.perform(get("/api/v1/me/chat-rooms").cookie(cookie));
	}

	private ResultActions requestChat(Cookie cookie, long receiverId) throws Exception {
		chat.sync();
		ResultActions result = mockMvc.perform(post("/api/v1/chat-requests")
				.cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"receiverId\": " + receiverId + " }"));
		chat.sync();
		return result;
	}

	private void send(Cookie cookie, long roomId, String content) throws Exception {
		chat.sync();
		mockMvc.perform(post("/api/v1/chat-rooms/{id}/messages", roomId)
						.cookie(cookie)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ \"content\": \"" + content + "\" }"))
				.andExpect(status().isCreated());
		chat.sync();
	}

	private List<Map<String, Object>> unreadChatNotifications(long userId) {
		return chat.notifications(userId).stream()
				.filter(notification -> "CHAT_MESSAGE_RECEIVED".equals(notification.get("type")))
				.filter(notification -> notification.get("read_at") == null)
				.toList();
	}

}
