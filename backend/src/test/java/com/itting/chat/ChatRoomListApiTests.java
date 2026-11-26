package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
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
 * 대화 목록: {@code GET /api/v1/me/chat-rooms}. 내가 들어갈 수 있는 대화방을 마지막 메시지가 새로운 것부터 한 번에 모두 준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatRoomListApiTests {

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

	long friend;

	long third;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		me = steps.createMember("me@example.com", "두더지왕");
		friend = steps.createMember("friend@example.com", "새벽산책");
		third = steps.createMember("third@example.com", "밤코딩");
		session = steps.login(mockMvc, "me@example.com");
	}

	@Test
	void 일대일과_모임_대화방을_마지막_메시지가_새로운_것부터_주고_메시지가_없는_방은_만든_시각으로_줄을_세운다() throws Exception {
		long direct = chat.insertDirectRoom(me, friend, ago(Duration.ofHours(3)));
		long oldMessage = chat.text(direct, friend, "이번 주 토요일 아침에 한강 어때요?", ago(Duration.ofMinutes(10)));
		long meetup = steps.insertMeetup(third, "알고리즘 스터디", now.plus(Duration.ofDays(3)), 6, null);
		long meetupRoom = chat.insertMeetupRoom(meetup, third, ago(Duration.ofHours(5)));
		chat.join(meetupRoom, me, ago(Duration.ofHours(4)));
		long newMessage = chat.text(meetupRoom, third, "이번 주는 그래프 문제 세 개 풀어 와요.", ago(Duration.ofMinutes(5)));
		long empty = chat.insertDirectRoom(me, third, ago(Duration.ofMinutes(1)));

		String body = list(session)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").value((Object) null))
				.andExpect(jsonPath("$.data.items.length()").value(3))
				// 메시지가 없는 방: 1분 전에 만들어 가장 새롭다
				.andExpect(jsonPath("$.data.items[0].type").value("DIRECT"))
				.andExpect(jsonPath("$.data.items[0].counterpart.nickname").value("밤코딩"))
				.andExpect(jsonPath("$.data.items[0].meetup").value((Object) null))
				.andExpect(jsonPath("$.data.items[0].lastMessage").value((Object) null))
				.andExpect(jsonPath("$.data.items[0].unreadCount").value(0))
				.andExpect(jsonPath("$.data.items[0].isReadOnly").value(false))
				// 모임 대화방
				.andExpect(jsonPath("$.data.items[1].type").value("MEETUP"))
				.andExpect(jsonPath("$.data.items[1].counterpart").value((Object) null))
				.andExpect(jsonPath("$.data.items[1].meetup.title").value("알고리즘 스터디"))
				.andExpect(jsonPath("$.data.items[1].lastMessage.type").value("TEXT"))
				.andExpect(jsonPath("$.data.items[1].lastMessage.content").value("이번 주는 그래프 문제 세 개 풀어 와요."))
				.andExpect(jsonPath("$.data.items[1].lastMessage.sender.nickname").value("밤코딩"))
				.andExpect(jsonPath("$.data.items[1].lastMessage.meetupCard").value((Object) null))
				.andExpect(jsonPath("$.data.items[1].lastMessage.images.length()").value(0))
				.andExpect(jsonPath("$.data.items[1].unreadCount").value(1))
				.andExpect(jsonPath("$.data.items[1].isReadOnly").value(false))
				// 1:1 대화방
				.andExpect(jsonPath("$.data.items[2].counterpart.nickname").value("새벽산책"))
				.andExpect(jsonPath("$.data.items[2].counterpart.isWithdrawn").value(false))
				.andExpect(jsonPath("$.data.items[2].lastMessage.content").value("이번 주 토요일 아침에 한강 어때요?"))
				.andExpect(jsonPath("$.data.items[2].unreadCount").value(1))
				.andReturn().getResponse().getContentAsString();

		assertThat(longs(body, "$.data.items[*].id")).containsExactly(empty, meetupRoom, direct);
		assertThat(number(body, "$.data.items[1].meetup.id")).isEqualTo(meetup);
		assertThat(number(body, "$.data.items[1].lastMessage.id")).isEqualTo(newMessage);
		assertThat(number(body, "$.data.items[2].lastMessage.id")).isEqualTo(oldMessage);
		assertThat(number(body, "$.data.items[2].counterpart.id")).isEqualTo(friend);
	}

	@Test
	void 안_읽은_수는_읽은_자리_뒤의_남이_보낸_사람_메시지만_센다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, ago(Duration.ofHours(1)));
		chat.insertMessage(room, "ACCEPTED", friend, null, ago(Duration.ofMinutes(50)));
		long first = chat.text(room, friend, "안녕하세요", ago(Duration.ofMinutes(40)));
		chat.text(room, me, "반가워요", ago(Duration.ofMinutes(30)));
		chat.text(room, friend, "주말에 뭐 하세요?", ago(Duration.ofMinutes(20)));
		chat.text(room, friend, "같이 달려요", ago(Duration.ofMinutes(10)));

		// 아무것도 읽지 않았으면 남이 보낸 사람 메시지 3개. 안내 메시지와 내 메시지는 세지 않는다.
		list(session).andExpect(jsonPath("$.data.items[0].unreadCount").value(3));

		chat.setLastRead(room, me, first);
		list(session).andExpect(jsonPath("$.data.items[0].unreadCount").value(2));
	}

	@Test
	void 마지막_메시지가_안내_메시지면_content가_null이다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, ago(Duration.ofHours(1)));
		chat.insertMessage(room, "ACCEPTED", friend, null, ago(Duration.ofMinutes(50)));

		list(session)
				.andExpect(jsonPath("$.data.items[0].lastMessage.type").value("ACCEPTED"))
				.andExpect(jsonPath("$.data.items[0].lastMessage.content").value((Object) null))
				.andExpect(jsonPath("$.data.items[0].lastMessage.sender.nickname").value("새벽산책"))
				.andExpect(jsonPath("$.data.items[0].unreadCount").value(0));
	}

	@Test
	void 상대가_나갔거나_탈퇴한_일대일_대화방은_목록에_남고_읽기_전용이다() throws Exception {
		long leftRoom = chat.insertDirectRoom(me, friend, ago(Duration.ofHours(2)));
		chat.leave(leftRoom, friend);
		long withdrawnRoom = chat.insertDirectRoom(me, third, ago(Duration.ofHours(1)));
		testUsers.withdraw(third);

		String body = list(session)
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[0].isReadOnly").value(true))
				.andExpect(jsonPath("$.data.items[0].counterpart.isWithdrawn").value(true))
				.andExpect(jsonPath("$.data.items[0].counterpart.nickname").value((Object) null))
				.andExpect(jsonPath("$.data.items[1].isReadOnly").value(true))
				.andExpect(jsonPath("$.data.items[1].counterpart.isWithdrawn").value(false))
				.andReturn().getResponse().getContentAsString();

		assertThat(longs(body, "$.data.items[*].id")).containsExactly(withdrawnRoom, leftRoom);
	}

	@Test
	void 내가_나간_대화방과_삭제된_모임의_대화방과_남의_대화방은_주지_않는다() throws Exception {
		long mine = chat.insertDirectRoom(me, friend, ago(Duration.ofHours(3)));
		long leftByMe = chat.insertDirectRoom(me, third, ago(Duration.ofHours(2)));
		chat.leave(leftByMe, me);
		long deletedMeetup = steps.insertMeetup(third, "지운 모임", now.plus(Duration.ofDays(1)), 4, null);
		long deletedRoom = chat.insertMeetupRoom(deletedMeetup, third, ago(Duration.ofHours(1)));
		chat.join(deletedRoom, me, ago(Duration.ofHours(1)));
		steps.deleteMeetup(deletedMeetup);
		chat.insertDirectRoom(friend, third, ago(Duration.ofMinutes(30)));
		// 취소된 모임의 대화방은 그대로 남는다.
		long canceledMeetup = steps.insertMeetup(third, "취소된 모임", now.plus(Duration.ofDays(1)), 4, null);
		long canceledRoom = chat.insertMeetupRoom(canceledMeetup, third, ago(Duration.ofMinutes(20)));
		chat.join(canceledRoom, me, ago(Duration.ofMinutes(20)));
		steps.cancelMeetup(canceledMeetup);

		String body = list(session).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(longs(body, "$.data.items[*].id")).containsExactly(canceledRoom, mine);
	}

	@Test
	void 대화가_하나도_없으면_빈_목록이다() throws Exception {
		list(session)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 로그인하지_않으면_401이고_온보딩을_마치지_않았으면_403이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/chat-rooms"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		testUsers.createEmailUser("newbie@example.com", CommunitySteps.PASSWORD);
		Cookie newbie = steps.login(mockMvc, "newbie@example.com");
		list(newbie)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	private ResultActions list(Cookie cookie) throws Exception {
		chat.sync();
		return mockMvc.perform(get("/api/v1/me/chat-rooms").cookie(cookie));
	}

	private Instant ago(Duration duration) {
		return now.minus(duration);
	}

	private static List<Long> longs(String body, String path) {
		List<Number> values = JsonPath.read(body, path);
		return values.stream().map(Number::longValue).toList();
	}

	private static long number(String body, String path) {
		return ((Number) JsonPath.read(body, path)).longValue();
	}

}
