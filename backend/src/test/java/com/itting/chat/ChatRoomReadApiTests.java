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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 읽음: {@code POST /api/v1/chat-rooms/{chatRoomId}/read}. 읽은 자리를 뒤로만 옮기고 그 뒤의 안 읽은 수를 준다. 그 대화방의
 * 안 읽은 새 메시지 알림도 읽음으로 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatRoomReadApiTests {

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

	long friend;

	long third;

	Cookie session;

	Cookie friendSession;

	long room;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		me = steps.createMember("me@example.com", "두더지왕");
		friend = steps.createMember("friend@example.com", "새벽산책");
		third = steps.createMember("third@example.com", "밤코딩");
		session = steps.login(mockMvc, "me@example.com");
		friendSession = steps.login(mockMvc, "friend@example.com");
		room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));
	}

	@Test
	void 읽은_자리를_옮기고_그_뒤의_안_읽은_수를_준다() throws Exception {
		long first = chat.text(room, friend, "안녕하세요", ago(30));
		long second = chat.text(room, friend, "주말에 뭐 하세요?", ago(20));
		long last = chat.text(room, friend, "같이 달려요", ago(10));

		read(session, room, first)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").value((Object) null))
				.andExpect(jsonPath("$.data.unreadCount").value(2));
		assertThat(chat.lastRead(room, me)).isEqualTo(first);

		read(session, room, last).andExpect(jsonPath("$.data.unreadCount").value(0));
		assertThat(chat.lastRead(room, me)).isEqualTo(last);

		// 이미 더 뒤까지 읽었으면 바꾸지 않는다.
		read(session, room, second)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.unreadCount").value(0));
		assertThat(chat.lastRead(room, me)).isEqualTo(last);

		// 상대의 읽은 자리는 바뀌지 않는다.
		assertThat(chat.lastRead(room, friend)).isNull();
		chat.sync();
		mockMvc.perform(get("/api/v1/me/chat-rooms").cookie(session))
				.andExpect(jsonPath("$.data.items[0].unreadCount").value(0));
	}

	@Test
	void 읽으면_그_대화방의_안_읽은_새_메시지_알림만_읽음으로_바뀐다() throws Exception {
		long otherRoom = chat.insertDirectRoom(me, third, now.minus(Duration.ofHours(1)));
		Cookie thirdSession = steps.login(mockMvc, "third@example.com");
		long message = sentId(send(friendSession, room, "안녕하세요"));
		send(thirdSession, otherRoom, "저도 안녕하세요");
		assertThat(unreadChatNotifications(me)).hasSize(2);

		read(session, room, message).andExpect(status().isOk());

		List<Map<String, Object>> unread = unreadChatNotifications(me);
		assertThat(unread).hasSize(1);
		assertThat(((Number) unread.getFirst().get("target_id")).longValue()).isEqualTo(otherRoom);

		// 읽은 뒤에 온 메시지는 새 알림 한 줄이 된다.
		send(friendSession, room, "또 보냈어요");
		assertThat(unreadChatNotifications(me)).hasSize(2);
	}

	@Test
	void 이_대화방의_메시지가_아니거나_messageId가_없으면_400이다() throws Exception {
		long otherRoom = chat.insertDirectRoom(me, third, now.minus(Duration.ofHours(1)));
		long elsewhere = chat.text(otherRoom, third, "다른 방의 메시지", ago(10));
		chat.text(room, friend, "안녕하세요", ago(5));

		for (String body : List.of("{ \"messageId\": " + elsewhere + " }", "{ \"messageId\": 999999999 }", "{}",
				"{ \"messageId\": null }")) {
			chat.sync();
			mockMvc.perform(post("/api/v1/chat-rooms/{id}/read", room)
							.cookie(session)
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
					.andExpect(jsonPath("$.error.fields[0].field").value("messageId"));
		}
		assertThat(chat.lastRead(room, me)).isNull();
	}

	@Test
	void 들어갈_수_없는_대화방은_404다() throws Exception {
		long others = chat.insertDirectRoom(friend, third, now.minus(Duration.ofHours(1)));
		long othersMessage = chat.text(others, friend, "남의 대화", ago(10));
		long message = chat.text(room, friend, "안녕하세요", ago(5));
		chat.leave(room, me);

		read(session, others, othersMessage)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
		read(session, room, message)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
		read(session, 999_999_999L, message).andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/chat-rooms/{id}/read", room)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ \"messageId\": " + message + " }"))
				.andExpect(status().isUnauthorized());
	}

	private ResultActions read(Cookie cookie, long roomId, long messageId) throws Exception {
		chat.sync();
		ResultActions result = mockMvc.perform(post("/api/v1/chat-rooms/{id}/read", roomId)
				.cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"messageId\": " + messageId + " }"));
		chat.sync();
		return result;
	}

	private ResultActions send(Cookie cookie, long roomId, String content) throws Exception {
		chat.sync();
		ResultActions result = mockMvc.perform(post("/api/v1/chat-rooms/{id}/messages", roomId)
				.cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"content\": \"" + content + "\" }"))
				.andExpect(status().isCreated());
		chat.sync();
		return result;
	}

	private static long sentId(ResultActions result) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString();
		return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
	}

	private List<Map<String, Object>> unreadChatNotifications(long userId) {
		return chat.notifications(userId).stream()
				.filter(notification -> "CHAT_MESSAGE_RECEIVED".equals(notification.get("type")))
				.filter(notification -> notification.get("read_at") == null)
				.toList();
	}

	private Instant ago(long minutes) {
		return now.minus(Duration.ofMinutes(minutes));
	}

}
