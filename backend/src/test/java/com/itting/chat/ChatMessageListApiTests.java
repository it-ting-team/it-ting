package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.itting.global.config.FrontendProperties;
import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 조회: {@code GET /api/v1/chat-rooms/{chatRoomId}/messages}. 새 것부터 커서 방식으로 나눠 주고, 안내 메시지와 내가
 * 들어오기 전의 메시지도 준다. 본문의 모임 링크는 모임 카드로 담는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatMessageListApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	FrontendProperties frontend;

	@Autowired
	MutableClock clock;

	Instant now;

	long me;

	long friend;

	long host;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		me = steps.createMember("me@example.com", "두더지왕");
		friend = steps.createMember("friend@example.com", "새벽산책");
		host = steps.createMember("host@example.com", "밤코딩");
		session = steps.login(mockMvc, "me@example.com");
	}

	@Test
	void 새_것부터_주고_안내_메시지도_함께_준다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, ago(60));
		Instant acceptedAt = ago(50).truncatedTo(ChronoUnit.SECONDS);
		long accepted = chat.insertMessage(room, "ACCEPTED", friend, null, acceptedAt);
		long hello = chat.text(room, me, "안녕하세요", ago(40));
		long reply = chat.text(room, friend, "반가워요", ago(30));

		String body = messages(session, room, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items.length()").value(3))
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").value((Object) null))
				.andExpect(jsonPath("$.data.items[0].type").value("TEXT"))
				.andExpect(jsonPath("$.data.items[0].content").value("반가워요"))
				.andExpect(jsonPath("$.data.items[0].sender.nickname").value("새벽산책"))
				.andExpect(jsonPath("$.data.items[0].meetupCard").value((Object) null))
				.andExpect(jsonPath("$.data.items[0].images.length()").value(0))
				.andExpect(jsonPath("$.data.items[1].sender.nickname").value("두더지왕"))
				.andExpect(jsonPath("$.data.items[2].type").value("ACCEPTED"))
				.andExpect(jsonPath("$.data.items[2].content").value((Object) null))
				.andExpect(jsonPath("$.data.items[2].sender.nickname").value("새벽산책"))
				.andExpect(jsonPath("$.data.items[2].createdAt").value(acceptedAt.toString()))
				.andReturn().getResponse().getContentAsString();

		assertThat(longs(body, "$.data.items[*].id")).containsExactly(reply, hello, accepted);
		assertThat(longs(body, "$.data.items[*].sender.id")).containsExactly(friend, me, friend);
	}

	@Test
	void size와_cursor로_더_오래된_메시지를_이어_받는다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, ago(60));
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			ids.add(chat.text(room, friend, "메시지 " + i, ago(50 - i)));
		}

		String first = messages(session, room, "?size=2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andExpect(jsonPath("$.data.nextCursor").isString())
				.andReturn().getResponse().getContentAsString();
		assertThat(longs(first, "$.data.items[*].id")).containsExactly(ids.get(4), ids.get(3));

		String cursor = JsonPath.read(first, "$.data.nextCursor");
		String second = messages(session, room, "?size=2&cursor=" + cursor)
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();
		assertThat(longs(second, "$.data.items[*].id")).containsExactly(ids.get(2), ids.get(1));

		// 이어 받는 사이에 새 메시지가 와도 이미 받은 것이 다시 오지 않는다.
		chat.text(room, friend, "그사이에 온 메시지", now);
		String third = messages(session, room, "?size=2&cursor=" + JsonPath.read(second, "$.data.nextCursor"))
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").value((Object) null))
				.andReturn().getResponse().getContentAsString();
		assertThat(longs(third, "$.data.items[*].id")).containsExactly(ids.get(0));
	}

	@Test
	void 보내지_않으면_30개를_주고_100개까지_받을_수_있다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, ago(600));
		for (int i = 0; i < 35; i++) {
			chat.text(room, friend, "메시지 " + i, ago(500 - i));
		}

		messages(session, room, "")
				.andExpect(jsonPath("$.data.items.length()").value(30))
				.andExpect(jsonPath("$.data.hasNext").value(true));
		messages(session, room, "?size=100")
				.andExpect(jsonPath("$.data.items.length()").value(35))
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	void 내가_들어오기_전의_메시지도_주고_조회해도_안_읽은_수가_그대로다() throws Exception {
		long meetup = steps.insertMeetup(host, "알고리즘 스터디", now.plus(Duration.ofDays(3)), 6, null);
		long room = chat.insertMeetupRoom(meetup, host, ago(300));
		long before = chat.text(room, host, "먼저 와 있었어요", ago(200));
		chat.join(room, me, ago(100));
		long joined = chat.insertMessage(room, "JOINED", me, null, ago(100));

		String body = messages(session, room, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].type").value("JOINED"))
				.andReturn().getResponse().getContentAsString();
		assertThat(longs(body, "$.data.items[*].id")).containsExactly(joined, before);

		assertThat(chat.lastRead(room, me)).isNull();
		chat.sync();
		mockMvc.perform(get("/api/v1/me/chat-rooms").cookie(session))
				.andExpect(jsonPath("$.data.items[0].unreadCount").value(1));
	}

	@Test
	void 메시지에_붙인_사진을_보낸_순서대로_준다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, ago(60));
		long message = chat.text(room, friend, null, ago(10));
		long second = steps.uploadImage(friend, "https://cdn.example.com/images/b.webp");
		long first = steps.uploadImage(friend, "https://cdn.example.com/images/a.webp");
		chat.attachImage(message, 2, second);
		chat.attachImage(message, 1, first);

		String body = messages(session, room, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].content").value((Object) null))
				.andExpect(jsonPath("$.data.items[0].images.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].images[0].url").value("https://cdn.example.com/images/a.webp"))
				.andExpect(jsonPath("$.data.items[0].images[1].url").value("https://cdn.example.com/images/b.webp"))
				.andReturn().getResponse().getContentAsString();

		assertThat(longs(body, "$.data.items[0].images[*].id")).containsExactly(first, second);
	}

	@Test
	void 본문의_우리_모임_링크는_모임의_지금_값을_담은_카드가_된다() throws Exception {
		Instant startsAt = now.plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
		long meetup = steps.insertMeetup(host, "토요일 한강 러닝", startsAt, 6, null);
		steps.apply(meetup, friend, "CONFIRMED");
		steps.apply(meetup, me, "PENDING");
		long other = steps.insertMeetup(host, "다른 모임", startsAt, 4, null);
		long room = chat.insertDirectRoom(me, friend, ago(60));
		chat.text(room, friend, "같이 뛸 사람 모으는 중이에요. " + link(meetup) + " 그리고 " + link(other), ago(10));

		String body = messages(session, room, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].meetupCard.title").value("토요일 한강 러닝"))
				.andExpect(jsonPath("$.data.items[0].meetupCard.startsAt").value(startsAt.toString()))
				.andExpect(jsonPath("$.data.items[0].meetupCard.capacity").value(6))
				// 주최자 1명 + 확정 1명. 대기 중인 신청은 세지 않는다.
				.andExpect(jsonPath("$.data.items[0].meetupCard.confirmedCount").value(2))
				.andReturn().getResponse().getContentAsString();

		// 링크가 여러 개면 첫 번째 것만 쓴다.
		assertThat(number(body, "$.data.items[0].meetupCard.meetupId")).isEqualTo(meetup);
	}

	@Test
	void 삭제된_모임과_없는_모임과_다른_사이트의_링크는_카드가_되지_않는다() throws Exception {
		long deleted = steps.insertMeetup(host, "지운 모임", now.plus(Duration.ofDays(2)), 4, null);
		steps.deleteMeetup(deleted);
		long alive = steps.insertMeetup(host, "있는 모임", now.plus(Duration.ofDays(2)), 4, null);
		long room = chat.insertDirectRoom(me, friend, ago(60));
		chat.text(room, friend, "지운 모임 " + link(deleted), ago(50));
		chat.text(room, friend, "없는 모임 " + link(999_999_999L), ago(40));
		chat.text(room, friend, "다른 사이트 https://example.com/meetups/" + alive, ago(30));
		chat.text(room, friend, "숫자 뒤에 글자 " + link(alive) + "abc", ago(20));
		// 취소된 모임은 삭제된 것이 아니라 카드로 보인다.
		long canceled = steps.insertMeetup(host, "취소된 모임", now.plus(Duration.ofDays(2)), 4, null);
		steps.cancelMeetup(canceled);
		chat.text(room, friend, link(canceled) + "?from=chat", ago(10));
		// 안내 메시지는 본문이 없어 카드도 없다.
		chat.insertMessage(room, "LEFT", friend, null, ago(5));

		messages(session, room, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(6))
				.andExpect(jsonPath("$.data.items[0].meetupCard").value((Object) null))
				.andExpect(jsonPath("$.data.items[1].meetupCard.title").value("취소된 모임"))
				.andExpect(jsonPath("$.data.items[2].meetupCard").value((Object) null))
				.andExpect(jsonPath("$.data.items[3].meetupCard").value((Object) null))
				.andExpect(jsonPath("$.data.items[4].meetupCard").value((Object) null))
				.andExpect(jsonPath("$.data.items[5].meetupCard").value((Object) null))
				// 카드가 되지 않아도 본문은 글자 그대로 보인다.
				.andExpect(jsonPath("$.data.items[5].content").value("지운 모임 " + link(deleted)));
	}

	@Test
	void size가_범위를_벗어나거나_cursor를_읽을_수_없으면_400이다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, ago(60));

		for (String query : List.of("?size=0", "?size=101", "?size=abc", "?cursor=%21%21%21", "?cursor=YWJj",
				"?cursor=LTU")) {
			messages(session, room, query)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		}
	}

	@Test
	void 들어갈_수_없는_대화방의_메시지는_404다() throws Exception {
		long others = chat.insertDirectRoom(friend, host, ago(60));
		chat.text(others, friend, "남의 대화", ago(10));
		long leftByMe = chat.insertDirectRoom(me, friend, ago(60));
		chat.leave(leftByMe, me);

		for (long room : List.of(999_999_999L, others, leftByMe)) {
			messages(session, room, "")
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
		}
		mockMvc.perform(get("/api/v1/chat-rooms/{id}/messages", others))
				.andExpect(status().isUnauthorized());
	}

	private ResultActions messages(Cookie cookie, long roomId, String query) throws Exception {
		chat.sync();
		return mockMvc.perform(get("/api/v1/chat-rooms/" + roomId + "/messages" + query).cookie(cookie));
	}

	private String link(long meetupId) {
		return frontend.frontendUrl() + "/meetups/" + meetupId;
	}

	private Instant ago(long minutes) {
		return now.minus(Duration.ofMinutes(minutes));
	}

	private static List<Long> longs(String body, String path) {
		List<Number> values = JsonPath.read(body, path);
		return values.stream().map(Number::longValue).toList();
	}

	private static long number(String body, String path) {
		return ((Number) JsonPath.read(body, path)).longValue();
	}

}
