package com.itting.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.itting.chat.domain.ChatMessage;
import com.itting.chat.domain.ChatRoomMember;
import com.itting.chat.repository.ChatMessageRepository;
import com.itting.chat.repository.ChatRoomMemberRepository;
import com.itting.chat.repository.ChatRoomReadRepository;
import com.itting.meetup.domain.Meetup;
import com.itting.meetup.domain.MeetupApplicationStatus;
import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupDetails;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.repository.MeetupParticipationRepository;
import com.itting.meetup.repository.MeetupRepository;
import com.itting.support.Members;
import com.itting.support.Members.Member;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MeetupDashboardApiTests.TestClock.class})
@Transactional
class MeetupDashboardApiTests {

	private static final String MY = "/api/v1/me/meetups";
	private static final String CALENDAR = "/api/v1/meetup-calendar";
	private static final String LIKE = "/api/v1/meetups/{id}/like";
	private static final String ALERT = "/api/v1/meetups/{id}/vacancy-alert";
	private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired TestUsers users;
	@Autowired OnboardingSteps onboarding;
	@Autowired MeetupRepository meetups;
	@Autowired MeetupParticipationRepository applications;
	@Autowired MeetupChatRooms rooms;
	@Autowired ChatRoomMemberRepository roomMembers;
	@Autowired ChatMessageRepository messages;
	@Autowired ChatRoomReadRepository chatRead;

	private Members members;

	@BeforeEach
	void setUp() {
		onboarding.clearNicknameHolds();
		members = new Members(mvc, users, onboarding, jdbc);
	}

	@Test
	void 찜과_구독을_각각_켜고_끄며_재요청에_구독을_보존한다() throws Exception {
		Member host = onboard("찜주최자");
		Member viewer = onboard("찜사용자");
		Meetup meetup = meetup(host, "찜 테스트", days(3), null, 4);
		long id = meetup.getId();

		mvc.perform(put(LIKE, Long.MAX_VALUE).cookie(viewer.session())).andExpect(status().isNotFound());
		mvc.perform(put(ALERT, Long.MAX_VALUE).cookie(viewer.session())).andExpect(status().isNotFound());
		mvc.perform(delete(LIKE, id)).andExpect(status().isUnauthorized());
		mvc.perform(delete(ALERT, id)).andExpect(status().isUnauthorized());
		mvc.perform(put(ALERT, id).cookie(viewer.session())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_LIKED"));
		mvc.perform(put(LIKE, id).cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isLiked").value(true));
		mvc.perform(put(ALERT, id).cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasVacancyAlert").value(true));
		mvc.perform(put(LIKE, id).cookie(viewer.session())).andExpect(status().isOk());
		assertThat(applications.findVacancySubscription(id, viewer.userId())).contains(true);
		mvc.perform(delete(ALERT, id).cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasVacancyAlert").value(false));
		assertThat(applications.findVacancySubscription(id, viewer.userId())).contains(false);
		mvc.perform(put(ALERT, id).cookie(viewer.session())).andExpect(status().isOk());
		for (int i = 0; i < 2; i++) {
			mvc.perform(delete(LIKE, id).cookie(viewer.session())).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.isLiked").value(false));
		}
		mvc.perform(delete(ALERT, id).cookie(viewer.session())).andExpect(status().isOk());
		assertThat(applications.findVacancySubscription(id, viewer.userId())).isEmpty();
	}

	@Test
	void 실제로_구독한_사람에게_모임_증원으로_생긴_빈자리를_알린다() throws Exception {
		Member host = onboard("증원주최");
		Member accepted = onboard("증원확정");
		Member subscriber = onboard("증원구독");
		Meetup meetup = meetup(host, "정원 증원", days(3), null, 2);
		mark(meetup, accepted, MeetupApplicationStatus.CONFIRMED);
		mvc.perform(put(LIKE, meetup.getId()).cookie(subscriber.session())).andExpect(status().isOk());
		mvc.perform(put(ALERT, meetup.getId()).cookie(subscriber.session())).andExpect(status().isOk());
		mvc.perform(patch("/api/v1/meetups/{id}", meetup.getId()).cookie(host.session()).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"정원 증원","category":"STUDY","description":null,"capacity":3,"supplies":null,
								 "isMessageEnabled":false,"startsAt":"%s","endsAt":null,
								 "mode":"ONLINE","place":null,"placeDetail":null,"joinUrl":null}
								""".formatted(days(3))))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM notification WHERE target_id = ? AND user_id = ? AND type = 'MEETUP_VACANCY_OPENED'
				""", Integer.class, meetup.getId(), subscriber.userId())).isEqualTo(1);
	}

	@Test
	void 내_모임_탭과_요약의_건수가_같고_위젯에는_대기_모임도_들어간다() throws Exception {
		Member viewer = onboard("탭사용자");
		Member other = onboard("탭상대방");
		Meetup hosted = meetup(viewer, "내가 연 모임", days(1), null, 4);
		mark(hosted, other, MeetupApplicationStatus.PENDING);
		Meetup joined = meetup(other, "확정 모임", days(2), null, 4);
		mark(joined, viewer, MeetupApplicationStatus.CONFIRMED);
		Meetup pending = meetup(other, "신청 대기 모임", days(3), null, 4);
		mark(pending, viewer, MeetupApplicationStatus.PENDING);
		Meetup expired = meetup(other, "마감된 신청", days(2).plusSeconds(60), NOW, 4);
		mark(expired, viewer, MeetupApplicationStatus.PENDING);
		Meetup past = meetup(viewer, "지난 모임", days(-1), null, 4);
		Meetup canceled = meetup(other, "취소된 모임", days(6), null, 4);
		mark(canceled, viewer, MeetupApplicationStatus.CONFIRMED);
		canceled.cancel(NOW);
		Meetup deleted = meetup(viewer, "삭제된 모임", days(7), null, 4);
		deleted.delete(NOW);
		meetups.flush();
		mvc.perform(put(LIKE, expired.getId()).cookie(viewer.session())).andExpect(status().isOk());
		mvc.perform(put(LIKE, pending.getId()).cookie(viewer.session())).andExpect(status().isOk());
		mvc.perform(put(LIKE, past.getId()).cookie(viewer.session())).andExpect(status().isOk());
		mvc.perform(put(LIKE, canceled.getId()).cookie(viewer.session())).andExpect(status().isOk());
		items(viewer, "UPCOMING", hosted, joined).andExpect(jsonPath("$.data.items[0].pendingCount").value(1))
				.andExpect(jsonPath("$.data.items[0].canEdit").value(true))
				.andExpect(jsonPath("$.data.items[1].pendingCount").value(0));
		items(viewer, "PENDING", expired, pending).andExpect(jsonPath("$.data.items[0].myStatus").value("REJECTED"));
		items(viewer, "HOSTING", hosted);
		items(viewer, "LIKED", pending, expired).andExpect(jsonPath("$.data.items[0].isLiked").value(true));
		items(viewer, "PAST", canceled, past).andExpect(jsonPath("$.data.items[0].recruitStatus").value("CANCELED"));
		mvc.perform(get(MY).param("tab", "UNKNOWN").cookie(viewer.session())).andExpect(status().isBadRequest());
		mvc.perform(get(MY + "/summary")).andExpect(status().isUnauthorized());
		mvc.perform(get(MY + "/summary").cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.counts.upcoming").value(2))
				.andExpect(jsonPath("$.data.counts.pending").value(2))
				.andExpect(jsonPath("$.data.counts.hosting").value(1))
				.andExpect(jsonPath("$.data.counts.liked").value(2))
				.andExpect(jsonPath("$.data.counts.past").value(2))
				.andExpect(jsonPath("$.data.upcomingItems[*].id", contains(id(hosted), id(joined), id(pending))));
	}

	@Test
	void 위젯은_시작이_가까운_모임을_다섯_개까지만_준다() throws Exception {
		Member viewer = onboard("일정위젯");
		for (int i = 1; i <= 7; i++) {
			meetup(viewer, "일정 " + i, days(i), null, 4);
		}
		mvc.perform(get(MY + "/summary").cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.counts.upcoming").value(7))
				.andExpect(jsonPath("$.data.upcomingItems.length()").value(5))
				.andExpect(jsonPath("$.data.upcomingItems[4].title").value("일정 5"));
	}

	@Test
	void 모임과_채팅_공통_조회는_읽은_ID_뒤의_남이_보낸_일반_메시지만_센다() throws Exception {
		Member host = onboard("채팅주최");
		Member viewer = onboard("채팅참여");
		Meetup meetup = meetup(host, "안 읽은 수", days(3), null, 4);
		long roomId = rooms.create(meetup.getId(), host.userId(), NOW);
		mark(meetup, viewer, MeetupApplicationStatus.CONFIRMED);
		roomMembers.saveAndFlush(ChatRoomMember.join(roomId, viewer.userId(), NOW, null));
		long earlier = messages.saveAndFlush(ChatMessage.text(roomId, host.userId(), "이전 글")).getId();
		messages.saveAndFlush(ChatMessage.joined(roomId, viewer.userId()));
		messages.saveAndFlush(ChatMessage.text(roomId, viewer.userId(), "내 글"));
		messages.saveAndFlush(ChatMessage.text(roomId, host.userId(), "이후 글"));
		items(viewer, "UPCOMING", meetup).andExpect(jsonPath("$.data.items[0].unreadCount").value(2));
		jdbc.update("UPDATE chat_room_member SET last_read_message_id = ? WHERE chat_room_id = ? AND user_id = ?",
				earlier, roomId, viewer.userId());
		items(viewer, "UPCOMING", meetup).andExpect(jsonPath("$.data.items[0].chatRoomId").value(roomId))
				.andExpect(jsonPath("$.data.items[0].unreadCount").value(1));
		assertThat(chatRead.countUnreadByRoomIds(viewer.userId(), List.of(roomId, Long.MAX_VALUE))).containsOnlyKeys(roomId)
				.containsEntry(roomId, 1L);
		jdbc.update("UPDATE chat_room_member SET left_at = UTC_TIMESTAMP(6) WHERE chat_room_id = ? AND user_id = ?",
				roomId, viewer.userId());
		items(viewer, "UPCOMING", meetup).andExpect(jsonPath("$.data.items[0].chatRoomId").value(nullValue()))
				.andExpect(jsonPath("$.data.items[0].unreadCount").value(0));
		assertThat(chatRead.countUnreadByRoomIds(viewer.userId(), List.of(roomId))).isEmpty();
		assertThat(chatRead.countUnreadByRoomIds(host.userId(), List.of(roomId))).containsKey(roomId);
		meetup.delete(NOW);
		meetups.flush();
		assertThat(chatRead.countUnreadByRoomIds(host.userId(), List.of(roomId))).isEmpty();
	}

	@Test
	void 캘린더는_한국_시간_월_경계와_보기_별_목록을_적용하고_내_요약은_같게_준다() throws Exception {
		Member viewer = onboard("달력사용자");
		Member other = onboard("달력상대방");
		Instant first = Instant.parse("2026-10-31T15:00:00Z");
		Instant next = Instant.parse("2026-11-30T15:00:00Z");
		Meetup hosted = meetup(viewer, "11월 첫날", first, null, 4);
		Meetup joined = meetup(other, "지난 확정", first.plusSeconds(60), null, 4);
		mark(joined, viewer, MeetupApplicationStatus.CONFIRMED);
		Meetup pending = meetup(other, "대기 중", days(1), null, 4);
		mark(pending, viewer, MeetupApplicationStatus.PENDING);
		Meetup unrelated = meetup(other, "관계 없는 모임", next.minusSeconds(1), null, 4);
		meetup(viewer, "10월 마지막 날", first.minusSeconds(1), null, 4);
		meetup(viewer, "12월 첫날", next, null, 4);
		Meetup canceled = meetup(viewer, "취소 모임", days(2), null, 4);
		canceled.cancel(NOW);
		Meetup deleted = meetup(viewer, "삭제 모임", days(2), null, 4);
		deleted.delete(NOW);
		meetups.flush();
		long room = rooms.create(hosted.getId(), viewer.userId(), NOW);
		roomMembers.flush();

		calendar(viewer, "MINE").andExpect(jsonPath("$.data.items[*].id", contains(id(hosted), id(joined), id(pending))))
				.andExpect(jsonPath("$.data.items[0].chatRoomId").value(room))
				.andExpect(jsonPath("$.data.summary.confirmed").value(1))
				.andExpect(jsonPath("$.data.summary.pending").value(1))
				.andExpect(jsonPath("$.data.summary.hosting").value(1));
		calendar(viewer, "ALL")
				.andExpect(jsonPath("$.data.items[*].id", contains(id(hosted), id(joined), id(pending), id(unrelated))))
				.andExpect(jsonPath("$.data.items[3].chatRoomId").value(nullValue()))
				.andExpect(jsonPath("$.data.summary.confirmed").value(1))
				.andExpect(jsonPath("$.data.summary.pending").value(1))
				.andExpect(jsonPath("$.data.summary.hosting").value(1));
		mvc.perform(get(CALENDAR).param("year", "10000").param("month", "11").cookie(viewer.session()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("year"));
		mvc.perform(get(CALENDAR).param("year", "2026").param("month", "13").cookie(viewer.session()))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("month"));
		for (String year : List.of("1000", "9999")) {
			mvc.perform(get(CALENDAR).param("year", year).param("month", year.equals("1000") ? "1" : "12")
							.cookie(viewer.session()))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
		}
	}

	private Member onboard(String nickname) throws Exception {
		return members.onboard("kan50-" + Integer.toUnsignedString(nickname.hashCode()) + "@example.test", nickname);
	}

	private Meetup meetup(Member host, String title, Instant start, Instant deadline, int capacity) {
		MeetupDetails details = new MeetupDetails(title, MeetupCategory.STUDY, null, capacity, null, false,
				start, null, MeetupMode.ONLINE, null, null, null);
		return meetups.save(new Meetup(host.userId(), details, deadline));
	}

	private void mark(Meetup meetup, Member user, MeetupApplicationStatus status) {
		Long id = applications.insertApplication(meetup.getId(), user.userId(), null, NOW);
		applications.changeStatus(id, status, NOW);
	}

	private ResultActions items(Member viewer, String tab, Meetup... expected) throws Exception {
		Integer[] ids = java.util.Arrays.stream(expected).map(MeetupDashboardApiTests::id).toArray(Integer[]::new);
		return mvc.perform(get(MY).param("tab", tab).cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id", contains(ids)));
	}

	private ResultActions calendar(Member viewer, String scope) throws Exception {
		return mvc.perform(get(CALENDAR).param("year", "2026").param("month", "11").param("scope", scope)
				.cookie(viewer.session())).andExpect(status().isOk());
	}

	private static int id(Meetup meetup) {
		return meetup.getId().intValue();
	}

	private static Instant days(int days) {
		return NOW.plus(Duration.ofDays(days));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClock {
		@Bean
		@Primary
		Clock dashboardTestClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}

}
