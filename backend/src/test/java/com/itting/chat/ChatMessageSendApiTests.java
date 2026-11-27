package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.itting.global.config.FrontendProperties;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 보내기: {@code POST /api/v1/chat-rooms/{chatRoomId}/messages}. 글만, 사진만, 또는 둘 다 보낸다. 대화방의 다른
 * 사람에게 새 메시지 알림이 가고, 보낸 사람의 읽은 자리가 그 메시지로 옮겨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatMessageSendApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	TestUsers testUsers;

	@Autowired
	FrontendProperties frontend;

	@Autowired
	MutableClock clock;

	Instant now;

	long me;

	long friend;

	long host;

	Cookie session;

	Cookie friendSession;

	long room;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		me = steps.createMember("me@example.com", "두더지왕");
		friend = steps.createMember("friend@example.com", "새벽산책");
		host = steps.createMember("host@example.com", "밤코딩");
		session = steps.login(mockMvc, "me@example.com");
		friendSession = steps.login(mockMvc, "friend@example.com");
		room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));
	}

	// ---- 정상 ----

	@Test
	void 글을_보내면_201과_만든_메시지를_주고_내_읽은_자리가_옮겨지고_상대에게_알림이_간다() throws Exception {
		long earlier = chat.text(room, friend, "주말에 뭐 하세요?", now.minus(Duration.ofMinutes(10)));

		String body = send(session, room, """
				{ "content": "좋아요. 7시에 여의나루역에서 봐요!", "imageIds": [] }
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").value((Object) null))
				.andExpect(jsonPath("$.data.type").value("TEXT"))
				.andExpect(jsonPath("$.data.content").value("좋아요. 7시에 여의나루역에서 봐요!"))
				.andExpect(jsonPath("$.data.sender.nickname").value("두더지왕"))
				.andExpect(jsonPath("$.data.meetupCard").value((Object) null))
				.andExpect(jsonPath("$.data.images.length()").value(0))
				.andExpect(jsonPath("$.data.createdAt").value(now.truncatedTo(ChronoUnit.SECONDS).toString()))
				.andReturn().getResponse().getContentAsString();
		long messageId = number(body, "$.data.id");

		assertThat(messageId).isGreaterThan(earlier);
		Map<String, Object> row = chat.messages(room).getLast();
		assertThat(((Number) row.get("id")).longValue()).isEqualTo(messageId);
		assertThat(row).containsEntry("type", "TEXT").containsEntry("content", "좋아요. 7시에 여의나루역에서 봐요!");
		assertThat(((Number) row.get("sender_id")).longValue()).isEqualTo(me);
		// 보낸 사람의 읽은 자리는 그 메시지로 옮겨지고, 상대의 것은 그대로다.
		assertThat(chat.lastRead(room, me)).isEqualTo(messageId);
		assertThat(chat.lastRead(room, friend)).isNull();
		assertThat(chat.notificationTypes(friend)).containsExactly("CHAT_MESSAGE_RECEIVED");
		assertThat(chat.notificationTypes(me)).isEmpty();
	}

	@Test
	void 여러_번_보내도_상대의_새_메시지_알림은_대화방마다_한_줄이다() throws Exception {
		send(session, room, text("첫 번째")).andExpect(status().isCreated());
		send(session, room, text("두 번째")).andExpect(status().isCreated());
		send(session, room, text("세 번째")).andExpect(status().isCreated());

		List<Map<String, Object>> notifications = chat.notifications(friend);
		assertThat(notifications).hasSize(1);
		assertThat(notifications.getFirst()).containsEntry("type", "CHAT_MESSAGE_RECEIVED");
		assertThat(((Number) notifications.getFirst().get("target_id")).longValue()).isEqualTo(room);
		assertThat(((Number) notifications.getFirst().get("actor_id")).longValue()).isEqualTo(me);
		assertThat(chat.messages(room)).hasSize(3);
	}

	@Test
	void 글_없이_사진만_보내면_content가_null이고_사진을_보낸_순서대로_붙인다() throws Exception {
		long first = steps.uploadImage(me, "https://cdn.example.com/images/a.webp");
		long second = steps.uploadImage(me, "https://cdn.example.com/images/b.webp");
		long third = steps.uploadImage(me, "https://cdn.example.com/images/c.webp");

		String body = send(session, room, """
				{ "content": null, "imageIds": [%d, %d, %d] }
				""".formatted(third, first, second))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.content").value((Object) null))
				.andExpect(jsonPath("$.data.images.length()").value(3))
				.andExpect(jsonPath("$.data.images[0].url").value("https://cdn.example.com/images/c.webp"))
				.andExpect(jsonPath("$.data.images[1].url").value("https://cdn.example.com/images/a.webp"))
				.andExpect(jsonPath("$.data.images[2].url").value("https://cdn.example.com/images/b.webp"))
				.andReturn().getResponse().getContentAsString();

		long messageId = number(body, "$.data.id");
		assertThat(longs(body, "$.data.images[*].id")).containsExactly(third, first, second);
		assertThat(chat.imageIdsOf(messageId)).containsExactly(third, first, second);
		assertThat(chat.messages(room).getLast().get("content")).isNull();
	}

	@Test
	void 글과_사진을_함께_보낼_수_있고_공백뿐인_글에_사진이_있으면_사진만_보낸_메시지가_된다() throws Exception {
		long first = steps.uploadImage(me, "https://cdn.example.com/images/a.webp");
		long second = steps.uploadImage(me, "https://cdn.example.com/images/b.webp");

		send(session, room, """
				{ "content": "  어제 찍은 사진이에요  ", "imageIds": [%d] }
				""".formatted(first))
				.andExpect(status().isCreated())
				// 앞뒤 공백을 다듬지 않고 받은 그대로 저장한다.
				.andExpect(jsonPath("$.data.content").value("  어제 찍은 사진이에요  "))
				.andExpect(jsonPath("$.data.images.length()").value(1));

		send(session, room, """
				{ "content": "   ", "imageIds": [%d] }
				""".formatted(second))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.content").value((Object) null))
				.andExpect(jsonPath("$.data.images.length()").value(1));
	}

	@Test
	void imageIds를_보내지_않아도_글만_보낼_수_있고_이모지는_한_글자로_센다() throws Exception {
		send(session, room, """
				{ "content": "사진 없이 보내요" }
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.data.images.length()").value(0));

		// 이모지 1,000개는 1,000자다(UTF-16으로는 2,000이다).
		send(session, room, text("😀".repeat(1000))).andExpect(status().isCreated());
	}

	@Test
	void 본문에_모임_링크가_있으면_응답에_모임_카드를_담는다() throws Exception {
		long meetup = steps.insertMeetup(host, "토요일 한강 러닝", now.plus(Duration.ofDays(2)), 6, null);

		String body = send(session, room, text("같이 가요 " + frontend.frontendUrl() + "/meetups/" + meetup))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.meetupCard.title").value("토요일 한강 러닝"))
				.andExpect(jsonPath("$.data.meetupCard.capacity").value(6))
				.andExpect(jsonPath("$.data.meetupCard.confirmedCount").value(1))
				.andReturn().getResponse().getContentAsString();

		assertThat(number(body, "$.data.meetupCard.meetupId")).isEqualTo(meetup);
	}

	@Test
	void 모임_대화방에서는_지금_있는_다른_사람_모두에게_알림이_가고_끝난_모임에도_보낼_수_있다() throws Exception {
		long meetup = steps.insertMeetup(host, "지난 모임", now.minus(Duration.ofDays(1)), 6, null);
		long meetupRoom = chat.insertMeetupRoom(meetup, host, now.minus(Duration.ofDays(3)));
		chat.join(meetupRoom, me, now.minus(Duration.ofDays(2)));
		chat.join(meetupRoom, friend, now.minus(Duration.ofDays(2)));
		long gone = steps.createMember("gone@example.com", "나간사람");
		chat.join(meetupRoom, gone, now.minus(Duration.ofDays(2)));
		chat.leave(meetupRoom, gone);

		send(session, meetupRoom, text("다들 잘 들어가셨어요?")).andExpect(status().isCreated());

		assertThat(chat.notificationTypes(host)).containsExactly("CHAT_MESSAGE_RECEIVED");
		assertThat(chat.notificationTypes(friend)).containsExactly("CHAT_MESSAGE_RECEIVED");
		assertThat(chat.notificationTypes(gone)).isEmpty();
		assertThat(chat.notificationTypes(me)).isEmpty();
	}

	// ---- 입력 오류 ----

	@Test
	void 글도_사진도_없거나_공백뿐이면_400이다() throws Exception {
		for (String body : List.of("{}", """
				{ "content": null, "imageIds": [] }
				""", """
				{ "content": "", "imageIds": [] }
				""", """
				{ "content": "   \\n ", "imageIds": [] }
				""")) {
			send(session, room, body)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
					.andExpect(jsonPath("$.error.fields[0].field").value("content"));
		}
		assertThat(chat.messages(room)).isEmpty();
		assertThat(chat.notificationTypes(friend)).isEmpty();
	}

	@Test
	void 글이_1001자이거나_사진이_4장이거나_같은_사진을_두_번_보내면_400이다() throws Exception {
		send(session, room, text("가".repeat(1001)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("content"));
		send(session, room, text("가".repeat(1000))).andExpect(status().isCreated());

		long a = steps.uploadImage(me, "https://cdn.example.com/images/a.webp");
		long b = steps.uploadImage(me, "https://cdn.example.com/images/b.webp");
		long c = steps.uploadImage(me, "https://cdn.example.com/images/c.webp");
		long d = steps.uploadImage(me, "https://cdn.example.com/images/d.webp");
		send(session, room, images(a, b, c, d))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("imageIds"));
		send(session, room, images(a, a))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("imageIds"));
	}

	@Test
	void 남의_사진과_없는_사진과_이미_글이나_메시지에_붙은_사진은_쓸_수_없다() throws Exception {
		long others = steps.uploadImage(friend, "https://cdn.example.com/images/friend.webp");
		long onPost = steps.uploadImage(me, "https://cdn.example.com/images/post.webp");
		long post = steps.insertPost(me, "FREE", "제목", "본문", now.minus(Duration.ofHours(1)));
		steps.attachImage(post, 1, onPost);
		long onMessage = steps.uploadImage(me, "https://cdn.example.com/images/message.webp");
		send(session, room, images(onMessage)).andExpect(status().isCreated());
		long usable = steps.uploadImage(me, "https://cdn.example.com/images/usable.webp");

		for (String body : List.of(images(others), images(999_999_999L), images(onPost), images(onMessage),
				images(usable, onMessage))) {
			send(session, room, body)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
					.andExpect(jsonPath("$.error.fields[0].field").value("imageIds"));
		}
		assertThat(chat.messages(room)).hasSize(1);
	}

	// ---- 들어갈 수 없는 대화방, 읽기 전용 ----

	@Test
	void 들어갈_수_없는_대화방에는_보낼_수_없다() throws Exception {
		long others = chat.insertDirectRoom(friend, host, now.minus(Duration.ofHours(1)));
		long leftByMe = chat.insertDirectRoom(me, host, now.minus(Duration.ofHours(1)));
		chat.leave(leftByMe, me);
		long deletedMeetup = steps.insertMeetup(host, "지운 모임", now.plus(Duration.ofDays(1)), 4, null);
		long deletedRoom = chat.insertMeetupRoom(deletedMeetup, host, now.minus(Duration.ofHours(1)));
		chat.join(deletedRoom, me, now.minus(Duration.ofHours(1)));
		steps.deleteMeetup(deletedMeetup);

		for (long target : List.of(999_999_999L, others, leftByMe, deletedRoom)) {
			send(session, target, text("안녕하세요"))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
		}
		assertThat(chat.messages(others)).isEmpty();
		assertThat(chat.messages(leftByMe)).isEmpty();

		mockMvc.perform(post("/api/v1/chat-rooms/{id}/messages", room)
						.contentType(MediaType.APPLICATION_JSON)
						.content(text("로그인 안 함")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 상대가_나간_일대일_대화방에는_보낼_수_없다() throws Exception {
		chat.leave(room, friend);

		send(session, room, text("아직 계세요?"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_READ_ONLY"))
				.andExpect(jsonPath("$.error.message").value("상대가 나가 메시지를 보낼 수 없는 대화방입니다."));

		assertThat(chat.messages(room)).isEmpty();
		assertThat(chat.notificationTypes(friend)).isEmpty();
	}

	@Test
	void 상대가_탈퇴한_일대일_대화방에는_보낼_수_없다() throws Exception {
		testUsers.withdraw(friend);

		send(session, room, text("아직 계세요?"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_READ_ONLY"));

		assertThat(chat.messages(room)).isEmpty();
	}

	private ResultActions send(Cookie cookie, long roomId, String body) throws Exception {
		chat.sync();
		ResultActions result = mockMvc.perform(post("/api/v1/chat-rooms/{id}/messages", roomId)
				.cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
		chat.sync();
		return result;
	}

	private static String text(String content) {
		return """
				{ "content": "%s", "imageIds": [] }
				""".formatted(content);
	}

	private static String images(long... imageIds) {
		List<String> ids = java.util.Arrays.stream(imageIds).mapToObj(String::valueOf).toList();
		return """
				{ "content": null, "imageIds": [%s] }
				""".formatted(String.join(", ", ids));
	}

	private static List<Long> longs(String body, String path) {
		List<Number> values = JsonPath.read(body, path);
		return values.stream().map(Number::longValue).toList();
	}

	private static long number(String body, String path) {
		return ((Number) JsonPath.read(body, path)).longValue();
	}

}
