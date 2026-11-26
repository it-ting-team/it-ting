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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화방 조회: {@code GET /api/v1/chat-rooms/{chatRoomId}}. 1:1은 상대와 겹치는 관심사를, 모임은 모임 · 공지 · 지금 있는 사람을
 * 주고, {@code leaveType}으로 나가기 버튼이 지금 하는 일을 알려 준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class ChatRoomDetailApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	TestUsers testUsers;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MutableClock clock;

	Instant now;

	long me;

	long friend;

	long host;

	Cookie session;

	Cookie hostSession;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		clock.setNow(now);
		me = steps.createMember("me@example.com", "두더지왕");
		friend = steps.createMember("friend@example.com", "새벽산책");
		host = steps.createMember("host@example.com", "밤코딩");
		session = steps.login(mockMvc, "me@example.com");
		hostSession = steps.login(mockMvc, "host@example.com");
	}

	// ---- 1:1 ----

	@Test
	void 일대일_대화방은_상대와_겹치는_관심사를_상대가_고른_순서로_준다() throws Exception {
		List<Long> interests = jdbcTemplate.queryForList("SELECT id FROM interest ORDER BY id LIMIT 4", Long.class);
		steps.setInterests(friend, List.of(interests.get(2), interests.get(3), interests.get(0)));
		steps.setInterests(me, List.of(interests.get(0), interests.get(1), interests.get(2)));
		long room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));

		String body = detail(session, room)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.type").value("DIRECT"))
				.andExpect(jsonPath("$.data.isReadOnly").value(false))
				.andExpect(jsonPath("$.data.leaveType").value("LEAVE_ROOM"))
				.andExpect(jsonPath("$.data.counterpart.nickname").value("새벽산책"))
				.andExpect(jsonPath("$.data.counterpart.isWithdrawn").value(false))
				.andExpect(jsonPath("$.data.sharedInterests.length()").value(2))
				.andExpect(jsonPath("$.data.sharedInterests[0].isShared").value(true))
				.andExpect(jsonPath("$.data.meetup").value((Object) null))
				.andExpect(jsonPath("$.data.notice").value((Object) null))
				.andExpect(jsonPath("$.data.canEditNotice").value(false))
				.andExpect(jsonPath("$.data.members.length()").value(0))
				.andReturn().getResponse().getContentAsString();

		assertThat(number(body, "$.data.id")).isEqualTo(room);
		assertThat(number(body, "$.data.counterpart.id")).isEqualTo(friend);
		// 상대가 고른 순서: 세 번째 관심사를 먼저 골랐다.
		assertThat(longs(body, "$.data.sharedInterests[*].id")).containsExactly(interests.get(2), interests.get(0));
	}

	@Test
	void 상대가_나간_일대일_대화방은_읽기_전용이고_나는_나갈_수_있다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));
		chat.leave(room, friend);

		detail(session, room)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isReadOnly").value(true))
				.andExpect(jsonPath("$.data.leaveType").value("LEAVE_ROOM"))
				.andExpect(jsonPath("$.data.counterpart.nickname").value("새벽산책"));
	}

	@Test
	void 상대가_탈퇴했으면_읽기_전용이고_겹치는_관심사는_비어_있다() throws Exception {
		List<Long> interests = jdbcTemplate.queryForList("SELECT id FROM interest ORDER BY id LIMIT 2", Long.class);
		steps.setInterests(friend, interests);
		steps.setInterests(me, interests);
		long room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));
		testUsers.withdraw(friend);

		detail(session, room)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isReadOnly").value(true))
				.andExpect(jsonPath("$.data.counterpart.isWithdrawn").value(true))
				.andExpect(jsonPath("$.data.counterpart.nickname").value((Object) null))
				.andExpect(jsonPath("$.data.sharedInterests.length()").value(0));
	}

	// ---- 모임 ----

	@Test
	void 모임_대화방은_모임_정보와_공지와_지금_있는_사람을_주최자_먼저_들어온_순서로_준다() throws Exception {
		Instant startsAt = now.plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
		long meetup = steps.insertMeetup(host, "알고리즘 스터디", startsAt, 6, null);
		steps.apply(meetup, me, "CONFIRMED");
		steps.apply(meetup, friend, "CONFIRMED");
		long gone = steps.createMember("gone@example.com", "나간사람");
		// 주최자가 방에 가장 늦게 들어온 것으로 넣어도 맨 앞이다.
		long room = chat.insertMeetupRoom(meetup, host, now.minus(Duration.ofHours(1)));
		chat.join(room, friend, now.minus(Duration.ofHours(3)));
		chat.join(room, me, now.minus(Duration.ofHours(2)));
		chat.join(room, gone, now.minus(Duration.ofHours(4)));
		chat.leave(room, gone);
		chat.setNotice(room, "매주 토요일 오후 2시에 모여요.");

		String body = detail(session, room)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.type").value("MEETUP"))
				.andExpect(jsonPath("$.data.isReadOnly").value(false))
				.andExpect(jsonPath("$.data.leaveType").value("CANCEL_PARTICIPATION"))
				.andExpect(jsonPath("$.data.counterpart").value((Object) null))
				.andExpect(jsonPath("$.data.sharedInterests.length()").value(0))
				.andExpect(jsonPath("$.data.meetup.title").value("알고리즘 스터디"))
				.andExpect(jsonPath("$.data.meetup.category").value("EXERCISE"))
				.andExpect(jsonPath("$.data.meetup.startsAt").value(startsAt.toString()))
				.andExpect(jsonPath("$.data.meetup.endsAt").value((Object) null))
				.andExpect(jsonPath("$.data.meetup.mode").value("ONLINE"))
				.andExpect(jsonPath("$.data.meetup.place").value((Object) null))
				.andExpect(jsonPath("$.data.meetup.placeDetail").value((Object) null))
				.andExpect(jsonPath("$.data.meetup.joinUrl").value("https://example.com/meet"))
				.andExpect(jsonPath("$.data.meetup.capacity").value(6))
				// 주최자 1명 + 확정 2명
				.andExpect(jsonPath("$.data.meetup.confirmedCount").value(3))
				.andExpect(jsonPath("$.data.meetup.isCanceled").value(false))
				.andExpect(jsonPath("$.data.notice").value("매주 토요일 오후 2시에 모여요."))
				.andExpect(jsonPath("$.data.canEditNotice").value(false))
				.andExpect(jsonPath("$.data.members.length()").value(3))
				.andExpect(jsonPath("$.data.members[0].user.nickname").value("밤코딩"))
				.andExpect(jsonPath("$.data.members[0].isHost").value(true))
				.andExpect(jsonPath("$.data.members[0].isMe").value(false))
				.andExpect(jsonPath("$.data.members[1].user.nickname").value("새벽산책"))
				.andExpect(jsonPath("$.data.members[1].isHost").value(false))
				.andExpect(jsonPath("$.data.members[2].user.nickname").value("두더지왕"))
				.andExpect(jsonPath("$.data.members[2].isMe").value(true))
				.andReturn().getResponse().getContentAsString();

		assertThat(number(body, "$.data.meetup.id")).isEqualTo(meetup);
		assertThat(number(body, "$.data.meetup.hostId")).isEqualTo(host);

		// 주최자에게는 공지를 고칠 수 있다고 알려 주고, 끝나지 않은 모임에서 주최자는 나갈 수 없다.
		detail(hostSession, room)
				.andExpect(jsonPath("$.data.canEditNotice").value(true))
				.andExpect(jsonPath("$.data.leaveType").value("NOT_ALLOWED"))
				.andExpect(jsonPath("$.data.members[0].isMe").value(true));
	}

	@Test
	void 시작_24시간_전이_지난_모임의_참여자는_나갈_수_없다() throws Exception {
		long room = meetupRoom(now.plus(Duration.ofHours(24)), null);

		// 정확히 24시간 전이면 이미 지난 것이다.
		detail(session, room).andExpect(jsonPath("$.data.leaveType").value("NOT_ALLOWED"));

		clock.setNow(now.minus(Duration.ofSeconds(1)));
		detail(session, room).andExpect(jsonPath("$.data.leaveType").value("CANCEL_PARTICIPATION"));
	}

	@Test
	void 시작했지만_끝나지_않은_모임에서는_누구도_나갈_수_없고_끝나는_시각이_되면_대화방에서만_나간다() throws Exception {
		long room = meetupRoom(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)));

		detail(session, room).andExpect(jsonPath("$.data.leaveType").value("NOT_ALLOWED"));
		detail(hostSession, room).andExpect(jsonPath("$.data.leaveType").value("NOT_ALLOWED"));

		clock.setNow(now.plus(Duration.ofHours(1)));
		detail(session, room).andExpect(jsonPath("$.data.leaveType").value("LEAVE_ROOM"));
		detail(hostSession, room).andExpect(jsonPath("$.data.leaveType").value("LEAVE_ROOM"));
	}

	@Test
	void 끝나는_시각이_없는_모임은_시작_시각이_되면_끝난_것으로_본다() throws Exception {
		long room = meetupRoom(now.plus(Duration.ofMinutes(10)), null);

		detail(session, room).andExpect(jsonPath("$.data.leaveType").value("NOT_ALLOWED"));

		clock.setNow(now.plus(Duration.ofMinutes(10)));
		detail(session, room).andExpect(jsonPath("$.data.leaveType").value("LEAVE_ROOM"));
	}

	@Test
	void 취소된_모임의_대화방은_주최자도_참여자도_대화방에서만_나간다() throws Exception {
		long meetup = steps.insertMeetup(host, "취소될 모임", now.plus(Duration.ofDays(3)), 4, null);
		steps.apply(meetup, me, "CONFIRMED");
		long room = chat.insertMeetupRoom(meetup, host, now.minus(Duration.ofHours(2)));
		chat.join(room, me, now.minus(Duration.ofHours(1)));
		steps.cancelMeetup(meetup);

		detail(session, room)
				.andExpect(jsonPath("$.data.leaveType").value("LEAVE_ROOM"))
				.andExpect(jsonPath("$.data.meetup.isCanceled").value(true));
		detail(hostSession, room).andExpect(jsonPath("$.data.leaveType").value("LEAVE_ROOM"));
	}

	// ---- 들어갈 수 없는 대화방 ----

	@Test
	void 없는_방과_남의_방과_내가_나간_방과_삭제된_모임의_방은_404다() throws Exception {
		long others = chat.insertDirectRoom(friend, host, now.minus(Duration.ofHours(1)));
		long leftByMe = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));
		chat.leave(leftByMe, me);
		long deletedMeetup = steps.insertMeetup(host, "지운 모임", now.plus(Duration.ofDays(3)), 4, null);
		long deletedRoom = chat.insertMeetupRoom(deletedMeetup, host, now.minus(Duration.ofHours(1)));
		chat.join(deletedRoom, me, now.minus(Duration.ofHours(1)));
		steps.deleteMeetup(deletedMeetup);

		for (long room : List.of(999_999_999L, others, leftByMe, deletedRoom)) {
			detail(session, room)
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"))
					.andExpect(jsonPath("$.error.message").value("대화방을 찾을 수 없습니다."));
		}
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		long room = chat.insertDirectRoom(me, friend, now.minus(Duration.ofHours(1)));

		mockMvc.perform(get("/api/v1/chat-rooms/{id}", room))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	/** 주최자와 내가 들어 있는 모임 대화방. 나는 확정 참여자다. */
	private long meetupRoom(Instant startsAt, Instant endsAt) {
		long meetup = steps.insertMeetup(host, "모임", startsAt, 4, null);
		if (endsAt != null) {
			jdbcTemplate.update("UPDATE meetup SET ends_at = ? WHERE id = ?", CommunitySteps.utc(endsAt), meetup);
		}
		steps.apply(meetup, me, "CONFIRMED");
		long room = chat.insertMeetupRoom(meetup, host, now.minus(Duration.ofDays(1)));
		chat.join(room, me, now.minus(Duration.ofHours(20)));
		return room;
	}

	private ResultActions detail(Cookie cookie, long roomId) throws Exception {
		chat.sync();
		return mockMvc.perform(get("/api/v1/chat-rooms/{id}", roomId).cookie(cookie));
	}

	private static List<Long> longs(String body, String path) {
		List<Number> values = JsonPath.read(body, path);
		return values.stream().map(Number::longValue).toList();
	}

	private static long number(String body, String path) {
		return ((Number) JsonPath.read(body, path)).longValue();
	}

}
