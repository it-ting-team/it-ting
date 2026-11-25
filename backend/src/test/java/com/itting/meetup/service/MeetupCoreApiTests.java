package com.itting.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.itting.chat.domain.ChatRoomMember;
import com.itting.chat.repository.ChatRoomMemberRepository;
import com.itting.meetup.domain.Meetup;
import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupDetails;
import com.itting.meetup.domain.MeetupMode;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MeetupCoreApiTests.FixedClockConfiguration.class})
@Transactional
class MeetupCoreApiTests {

	private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");
	private static final Instant SATURDAY_START = Instant.parse("2026-11-27T15:00:00Z");
	private static final String COLLECTION = "/api/v1/meetups";
	private static final String ITEM = COLLECTION + "/{meetupId}";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	TestUsers users;

	@Autowired
	OnboardingSteps steps;

	@Autowired
	MeetupRepository meetups;

	@Autowired
	MeetupReminderJob reminders;

	@MockitoSpyBean
	ChatRoomMemberRepository roomMembers;

	private Members members;

	@BeforeEach
	void setUp() {
		steps.clearNicknameHolds();
		members = new Members(mvc, users, steps, jdbc);
	}

	@Test
	void 목록은_한국_날짜와_검색어를_그대로_적용하고_모집_중을_먼저_준다() throws Exception {
		Member host = onboard("모임주최자");
		Member participant = onboard("모임참여자");
		Meetup closed = save(host, "주말 100% 러닝", SATURDAY_START, 2);
		Meetup open = save(host, "토요일 100% 러닝", SATURDAY_START.plusSeconds(60), 4);
		apply(closed, participant, "CONFIRMED", NOW);
		save(host, "표시하지 않는 100% 러닝", SATURDAY_START.minusSeconds(1), 2);
		save(host, "비슷해 보여도 다른 100점 러닝", SATURDAY_START.plusSeconds(120), 2);
		Meetup canceled = save(host, "취소한 100% 러닝", SATURDAY_START, 4);
		canceled.cancel(NOW);
		meetups.flush();

		mvc.perform(get(COLLECTION).cookie(host.session())
						.param("keyword", " 100% 러닝 ").param("categories", "EXERCISE,GAME")
						.param("dateFrom", "2026-11-28").param("dateTo", "2026-11-28")
						.param("mode", "ONLINE").param("sort", "MOST_JOINED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalItems").value(2))
				.andExpect(jsonPath("$.data.items[*].id", contains(open.getId().intValue(), closed.getId().intValue())))
				.andExpect(jsonPath("$.data.items[*].recruitStatus", contains("OPEN", "CLOSED")));
		mvc.perform(get(COLLECTION).cookie(host.session()).param("page", "2"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 목록은_잘못된_기간과_페이지_크기와_미로그인을_막는다() throws Exception {
		Member viewer = onboard("목록확인");
		mvc.perform(get(COLLECTION)).andExpect(status().isUnauthorized());
		mvc.perform(get(COLLECTION).cookie(viewer.session()).param("size", "8"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("size"));
		mvc.perform(get(COLLECTION).cookie(viewer.session()).param("dateFrom", "2026-11-29")
						.param("dateTo", "2026-11-28"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("dateTo"));
	}

	@Test
	void 온라인_모임을_만들면_방과_주최자_멤버를_함께_저장하고_멤버에게만_방을_공개한다() throws Exception {
		Member host = onboard("온라인주최");
		Member guest = onboard("온라인방문");
		mvc.perform(post(COLLECTION).cookie(host.session()).contentType(MediaType.APPLICATION_JSON)
						.content(body(SATURDAY_START, "온라인 공부", 4)))
				.andExpect(status().isCreated());
		Long meetupId = jdbc.queryForObject("SELECT id FROM meetup WHERE host_id = ?", Long.class, host.userId());
		Long roomId = jdbc.queryForObject("SELECT id FROM chat_room WHERE meetup_id = ? AND type = 'MEETUP'",
				Long.class, meetupId);
		assertThat(roomMembers.findById(new ChatRoomMember.Key(roomId, host.userId())))
				.hasValueSatisfying(member -> {
					assertThat(member.getJoinedAt()).isEqualTo(NOW);
					assertThat(member.getLeftAt()).isNull();
					assertThat(member.getLastReadMessageId()).isNull();
				});
		mvc.perform(get(ITEM, meetupId).cookie(host.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.chatRoomId").value(roomId))
				.andExpect(jsonPath("$.data.confirmedCount").value(1))
				.andExpect(jsonPath("$.data.recruitStatus").value("OPEN"));
		mvc.perform(get(ITEM, meetupId).cookie(guest.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.chatRoomId").value(nullValue()));
		roomMembers.saveAndFlush(ChatRoomMember.join(roomId, guest.userId(), NOW, null));
		mvc.perform(get(ITEM, meetupId).cookie(guest.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.chatRoomId").value(roomId));
		jdbc.update("UPDATE chat_room_member SET left_at = UTC_TIMESTAMP(6) WHERE chat_room_id = ? AND user_id = ?",
				roomId, guest.userId());
		mvc.perform(get(ITEM, meetupId).cookie(guest.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.chatRoomId").value(nullValue()));
	}

	@Test
	void 오프라인_모임을_만들면_실제_모임과_방의_ID를_반환한다() throws Exception {
		Member host = onboard("오프라인주최");
		String offline = body(SATURDAY_START, "한강 러닝", 4)
				.replace("\"ONLINE\"", "\"OFFLINE\"").replace("\"place\":null", "\"place\":\"한강공원\"")
				.replace("\"https://example.com/meet\"", "null");
		ResultActions response = mvc.perform(post(COLLECTION).cookie(host.session()).contentType(MediaType.APPLICATION_JSON)
						.content(offline))
				.andExpect(status().isCreated());
		roomMembers.flush();
		Long meetupId = jdbc.queryForObject("SELECT id FROM meetup WHERE host_id = ?", Long.class, host.userId());
		Long roomId = jdbc.queryForObject("SELECT chat_room_id FROM chat_room_member WHERE user_id = ?",
				Long.class, host.userId());
		response.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.meetupId").value(meetupId))
				.andExpect(jsonPath("$.data.chatRoomId").value(roomId))
				.andExpect(jsonPath("$.error").value(nullValue()));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 주최자_멤버_저장이_실패하면_모임과_대화방도_롤백한다() throws Exception {
		Member host = onboard("롤백주최자");
		doAnswer(invocation -> {
			ChatRoomMember member = invocation.getArgument(0);
			assertThat(jdbc.queryForObject("""
					SELECT COUNT(*) FROM meetup m JOIN chat_room r ON r.meetup_id = m.id
					WHERE m.host_id = ? AND r.id = ?
					""", Integer.class, host.userId(), member.getChatRoomId())).isEqualTo(1);
			throw new DataIntegrityViolationException("test: chat membership insert failed");
		}).when(roomMembers).save(any(ChatRoomMember.class));
		mvc.perform(post(COLLECTION).cookie(host.session()).contentType(MediaType.APPLICATION_JSON)
						.content(body(SATURDAY_START, "롤백 대상 모임", 4)))
				.andExpect(status().isInternalServerError());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meetup WHERE host_id = ?", Integer.class, host.userId()))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_room WHERE type = 'MEETUP'", Integer.class))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_room_member WHERE user_id = ?", Integer.class, host.userId()))
				.isZero();
	}

	@Test
	void 대화방_외래키는_없는_모임을_가리키는_행을_막는다() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO chat_room (type, meetup_id, created_at, updated_at)
				VALUES ('MEETUP', ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 생성_입력에서_과거_시작과_장소가_없는_오프라인을_거부한다() throws Exception {
		Member host = onboard("생성검증");
		String offline = body(NOW.minusSeconds(1), "잘못된 오프라인", 3).replace("\"ONLINE\"", "\"OFFLINE\"")
				.replace("\"https://example.com/meet\"", "null");
		mvc.perform(post(COLLECTION).cookie(host.session()).contentType(MediaType.APPLICATION_JSON).content(offline))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field", contains("place", "startsAt")));
		assertThat(meetups.count()).isZero();
	}

	@Test
	void 상세의_온라인_링크는_주최자와_확정자에게_공개한다() throws Exception {
		Member host = onboard("상세주최자");
		Member confirmed = onboard("상세확정자");
		Member visitor = onboard("상세방문자");
		Meetup meetup = save(host, "온라인 상세", SATURDAY_START, 4);
		apply(meetup, confirmed, "CONFIRMED", NOW);
		like(meetup, visitor, true);

		mvc.perform(get(ITEM, meetup.getId()).cookie(visitor.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.joinUrl").value(nullValue()))
				.andExpect(jsonPath("$.data.myStatus").value("NONE"))
				.andExpect(jsonPath("$.data.sharedInterestParticipantCount").value(2))
				.andExpect(jsonPath("$.data.isLiked").value(true))
				.andExpect(jsonPath("$.data.hasVacancyAlert").value(true));
		mvc.perform(get(ITEM, meetup.getId()).cookie(confirmed.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.joinUrl").value("https://example.com/meet"))
				.andExpect(jsonPath("$.data.participants[0].isHost").value(true))
				.andExpect(jsonPath("$.data.participants[1].isMe").value(true));
		mvc.perform(get(ITEM, meetup.getId()).cookie(host.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.myStatus").value("HOST"))
				.andExpect(jsonPath("$.data.canCancel").value(true));
		mvc.perform(get(ITEM, Long.MAX_VALUE).cookie(host.session())).andExpect(status().isNotFound());
	}

	@Test
	void 수정은_주최자만_가능하며_증원하면_구독자에게_빈자리_알림을_보낸다() throws Exception {
		Member host = onboard("수정주최자");
		Member confirmed = onboard("수정확정자");
		Member pending = onboard("수정대기자");
		Meetup meetup = save(host, "이전 이름", SATURDAY_START, 2);
		apply(meetup, confirmed, "CONFIRMED", NOW);
		apply(meetup, pending, "PENDING", NOW);
		like(meetup, pending, true);
		String changed = body(SATURDAY_START, "새 이름", 3);

		mvc.perform(patch(ITEM, meetup.getId()).cookie(pending.session()).contentType(MediaType.APPLICATION_JSON)
						.content(changed)).andExpect(status().isForbidden());
		for (int i = 0; i < 2; i++) {
			mvc.perform(patch(ITEM, meetup.getId()).cookie(host.session()).contentType(MediaType.APPLICATION_JSON)
							.content(changed)).andExpect(status().isOk());
		}
		mvc.perform(get(ITEM, meetup.getId()).cookie(host.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("새 이름"))
				.andExpect(jsonPath("$.data.recruitStatus").value("OPEN"));
		assertThat(notifications(meetup, "MEETUP_UPDATED")).isEqualTo(2);
		assertThat(notifications(meetup, "MEETUP_VACANCY_OPENED")).isEqualTo(1);
	}

	@Test
	void 수정은_12시간_경계부터_막고_선택값의_키를_생략할_수_없다() throws Exception {
		Member host = onboard("수정경계");
		Instant boundary = NOW.plus(Duration.ofHours(12));
		Meetup tooLate = save(host, "수정 경계", boundary, 4);
		Meetup later = save(host, "수정 가능", SATURDAY_START, 4);
		mvc.perform(patch(ITEM, tooLate.getId()).cookie(host.session()).contentType(MediaType.APPLICATION_JSON)
						.content(body(boundary, "새 이름", 4))).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_EDIT_NOT_ALLOWED"));
		mvc.perform(patch(ITEM, later.getId()).cookie(host.session()).contentType(MediaType.APPLICATION_JSON)
						.content(body(SATURDAY_START, "새 이름", 4).replace("\"description\":null,", "")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 취소하면_대기_신청을_닫고_확정은_남기며_재호출에_알림이_늘지_않는다() throws Exception {
		Member host = onboard("취소주최자");
		Member confirmed = onboard("취소확정자");
		Member pending = onboard("취소대기자");
		Meetup meetup = save(host, "취소할 모임", SATURDAY_START, 4);
		apply(meetup, confirmed, "CONFIRMED", NOW);
		apply(meetup, pending, "PENDING", NOW);
		mvc.perform(delete(ITEM, meetup.getId()).cookie(host.session())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_DELETE_NOT_ALLOWED"));
		for (int i = 0; i < 2; i++) {
			mvc.perform(post(ITEM + "/cancel", meetup.getId()).cookie(host.session())).andExpect(status().isOk());
		}
		mvc.perform(get(ITEM, meetup.getId()).cookie(pending.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recruitStatus").value("CANCELED"))
				.andExpect(jsonPath("$.data.myStatus").value("MEETUP_CANCELED"))
				.andExpect(jsonPath("$.data.confirmedCount").value(2));
		assertThat(notifications(meetup, "MEETUP_CANCELED")).isEqualTo(2);
	}

	@Test
	void 확정자가_없는_모임은_삭제하면서_대기자에게_알린다() throws Exception {
		Member host = onboard("삭제주최자");
		Member pending = onboard("삭제대기자");
		Meetup meetup = save(host, "삭제할 모임", SATURDAY_START, 4);
		apply(meetup, pending, "PENDING", NOW);
		mvc.perform(post(ITEM + "/cancel", meetup.getId()).cookie(host.session())).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_CANCEL_NOT_ALLOWED"));
		mvc.perform(delete(ITEM, meetup.getId()).cookie(host.session())).andExpect(status().isOk());
		mvc.perform(delete(ITEM, meetup.getId()).cookie(host.session())).andExpect(status().isNotFound());
		mvc.perform(get(ITEM, meetup.getId()).cookie(host.session())).andExpect(status().isNotFound());
		mvc.perform(get(COLLECTION).cookie(host.session()))
				.andExpect(jsonPath("$.data.items.length()").value(0));
		assertThat(notifications(meetup, "MEETUP_DELETED")).isEqualTo(1);
	}

	@Test
	void 임박_알림은_기준_시각의_확정자를_포함하고_누락된_과거_알림을_다시_보내지_않는다() throws Exception {
		Member host = onboard("임박주최자");
		Member confirmed = onboard("일찍확정");
		Member late = onboard("늦게확정");
		Instant due = NOW.plus(Duration.ofMinutes(10));
		Meetup meetup = save(host, "임박한 모임", due.plus(Duration.ofHours(24)), 4);
		Meetup missed = save(host, "늦게 확인한 모임", due.plus(Duration.ofHours(24)).minusSeconds(120), 4);
		apply(meetup, confirmed, "CONFIRMED", due.minusSeconds(1));
		apply(meetup, late, "CONFIRMED", due.plusSeconds(1));

		reminders.sendDueReminders(due.minusSeconds(121));
		assertThat(notifications(meetup, "MEETUP_START_REMINDER")).isZero();
		reminders.sendDueReminders(due.plusSeconds(240));
		reminders.sendDueReminders(due.plusSeconds(241));
		assertThat(notifications(meetup, "MEETUP_START_REMINDER")).isEqualTo(2);
		assertThat(notifications(missed, "MEETUP_START_REMINDER")).isZero();

		Instant moved = meetup.getDetails().startsAt().plusSeconds(30);
		mvc.perform(patch(ITEM, meetup.getId()).cookie(host.session()).contentType(MediaType.APPLICATION_JSON)
						.content(body(moved, "임박한 모임", 4))).andExpect(status().isOk());
		reminders.sendDueReminders(due.plusSeconds(241));
		assertThat(notifications(meetup, "MEETUP_START_REMINDER")).isEqualTo(5);
		reminders.sendDueReminders(moved.minus(Duration.ofHours(1)));
		assertThat(notifications(meetup, "MEETUP_START_REMINDER")).isEqualTo(8);
	}

	private Member onboard(String nickname) throws Exception {
		return members.onboard("kan48-" + Integer.toUnsignedString(nickname.hashCode()) + "@example.com", nickname);
	}

	private Meetup save(Member host, String title, Instant starts, int capacity) {
		MeetupDetails details = new MeetupDetails(title, MeetupCategory.EXERCISE, null, capacity, null, true,
				starts, null, MeetupMode.ONLINE, null, null, "https://example.com/meet");
		return meetups.saveAndFlush(new Meetup(host.userId(), details, null));
	}

	private void apply(Meetup meetup, Member member, String state, Instant at) {
		jdbc.update("""
				INSERT INTO meetup_application (meetup_id, user_id, status, created_at, status_changed_at)
				VALUES (?, ?, ?, ?, ?)
				""", meetup.getId(), member.userId(), state, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
				LocalDateTime.ofInstant(at, ZoneOffset.UTC));
	}

	private void like(Meetup meetup, Member member, boolean vacancy) {
		jdbc.update("""
				INSERT INTO meetup_like (meetup_id, user_id, vacancy_alert, created_at, updated_at)
				VALUES (?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", meetup.getId(), member.userId(), vacancy);
	}

	private int notifications(Meetup meetup, String type) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE target_id = ? AND type = ?",
				Integer.class, meetup.getId(), type);
	}

	private static String body(Instant starts, String title, int capacity) {
		return """
				{"title":"%s","category":"EXERCISE","description":null,"capacity":%d,"supplies":null,
				 "isMessageEnabled":true,"startsAt":"%s","endsAt":null,
				 "mode":"ONLINE","place":null,"placeDetail":null,"joinUrl":"https://example.com/meet"}
				""".formatted(title, capacity, starts);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {
		@Bean
		@Primary
		Clock meetupTestClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}

}
