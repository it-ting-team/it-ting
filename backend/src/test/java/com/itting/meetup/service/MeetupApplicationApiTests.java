package com.itting.meetup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.itting.chat.domain.ChatMessage;
import com.itting.chat.repository.ChatMessageRepository;
import com.itting.meetup.domain.Meetup;
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
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, MeetupApplicationApiTests.TestClock.class})
@Transactional
class MeetupApplicationApiTests {

	private static final String MEETUP = "/api/v1/meetups/{id}";
	private static final String APPLICATION = "/api/v1/meetup-applications/{id}";
	private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");
	private static final Instant START = NOW.plus(Duration.ofDays(3));

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired TestUsers users;
	@Autowired OnboardingSteps onboarding;
	@Autowired MeetupRepository meetups;
	@Autowired MeetupParticipationRepository applications;
	@Autowired MeetupChatRooms chatRooms;
	@Autowired MeetupApplicationDeadlineJob deadlines;
	@Autowired TransactionTemplate transactions;
	@MockitoSpyBean ChatMessageRepository messages;

	private Members members;

	@BeforeEach
	void setUp() {
		onboarding.clearNicknameHolds();
		members = new Members(mvc, users, onboarding, jdbc);
	}

	@Test
	void 신청과_철회_후_재신청은_같은_신청_ID를_사용한다() throws Exception {
		Member host = onboard("신청주최자");
		Member applicant = onboard("모임신청자");
		Room room = room(host, 4, START, null, false);
		apply(room, host, "{}").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("OWN_MEETUP_NOT_ALLOWED"));
		apply(room, applicant, "{\"message\":\"" + "가".repeat(101) + "\"}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0].field").value("message"));
		long id = applicationId(apply(room, applicant, "{\"message\":\"반갑습니다\"}"));
		assertThat(jdbc.queryForObject("SELECT message FROM meetup_application WHERE id = ?", String.class, id)).isNull();
		apply(room, applicant, "{}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_ALREADY_APPLIED"));
		for (int call = 0; call < 2; call++) {
			action(MEETUP, "/withdraw-application", room.meetupId(), applicant).andExpect(status().isOk());
		}
		assertThat(state(id)).isEqualTo("WITHDRAWN");
		assertThat(notifications(room, "MEETUP_APPLICATION_RECEIVED")).isEqualTo(1);
		assertThat(applicationId(apply(room, applicant, "{}"))).isEqualTo(id);
		assertThat(state(id)).isEqualTo("PENDING");
		assertThat(notifications(room, "MEETUP_APPLICATION_RECEIVED")).isEqualTo(2);
	}

	@Test
	void 주최자만_목록과_수락과_내보내기를_처리하고_수락은_방에_입장시킨다() throws Exception {
		Member host = onboard("수락주최자");
		Member applicant = onboard("수락신청자");
		Member waiting = onboard("수락대기자");
		Room room = room(host, 2, START, null, true);
		long id = applicationId(apply(room, applicant, "{\"message\":\"같이 해요\"}"));
		long waitingId = applicationId(apply(room, waiting, "{}"));
		mvc.perform(get(MEETUP + "/applications", room.meetupId()).cookie(waiting.session()))
				.andExpect(status().isForbidden());
		action(APPLICATION, "/accept", id, waiting).andExpect(status().isForbidden());
		messages.saveAndFlush(ChatMessage.text(room.roomId(), host.userId(), "입장 전 일반 메시지"));
		for (int call = 0; call < 2; call++) {
			action(APPLICATION, "/accept", id, host).andExpect(status().isOk());
		}
		action(APPLICATION, "/accept", waitingId, host).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_FULL"));
		mvc.perform(get(MEETUP + "/applications", room.meetupId()).cookie(host.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.confirmedCount").value(2))
				.andExpect(jsonPath("$.data.pendingCount").value(1))
				.andExpect(jsonPath("$.data.items[*].status", contains("PENDING", "CONFIRMED")))
				.andExpect(jsonPath("$.data.items[1].message").value("같이 해요"))
				.andExpect(jsonPath("$.data.items[1].sharedInterestCount").value(3));
		mvc.perform(get(MEETUP, room.meetupId()).cookie(applicant.session()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.chatRoomId").value(room.roomId()));
		assertThat(jdbc.queryForObject("""
				SELECT last_read_message_id FROM chat_room_member WHERE chat_room_id = ? AND user_id = ?
				""", Long.class, room.roomId(), applicant.userId())).isNull();
		assertThat(announcements(room, "JOINED", applicant)).isEqualTo(1);
		assertThat(notifications(room, "MEETUP_APPLICATION_ACCEPTED")).isEqualTo(1);
		action(MEETUP, "/withdraw-application", room.meetupId(), applicant).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_APPLICATION_ALREADY_CONFIRMED"));
		action(APPLICATION, "/kick", waitingId, host).andExpect(status().isNotFound());
		for (int call = 0; call < 2; call++) {
			action(APPLICATION, "/kick", id, host).andExpect(status().isOk());
		}
		assertThat(state(id)).isEqualTo("KICKED");
		assertThat(announcements(room, "LEFT", applicant)).isEqualTo(1);
		assertThat(notifications(room, "MEETUP_KICKED")).isEqualTo(1);
		mvc.perform(get(MEETUP, room.meetupId()).cookie(applicant.session()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.chatRoomId").value(nullValue()));
	}

	@Test
	void 확정자가_참여를_취소하면_대화방에서_나가고_마감_전_빈자리를_알린다() throws Exception {
		Member host = onboard("취소주최자");
		Member attendee = onboard("취소확정자");
		Member subscriber = onboard("빈자리구독자");
		Room room = room(host, 2, START, null, false);
		long id = applicationId(apply(room, attendee, "{}"));
		action(APPLICATION, "/accept", id, host).andExpect(status().isOk());
		jdbc.update("""
				INSERT INTO meetup_like (meetup_id, user_id, vacancy_alert, created_at, updated_at)
				VALUES (?, ?, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", room.meetupId(), subscriber.userId());
		for (int call = 0; call < 2; call++) {
			action(MEETUP, "/cancel-participation", room.meetupId(), attendee).andExpect(status().isOk());
		}
		assertThat(state(id)).isEqualTo("CANCELED");
		assertThat(announcements(room, "LEFT", attendee)).isEqualTo(1);
		assertThat(notifications(room, "MEETUP_PARTICIPATION_CANCELED")).isEqualTo(1);
		assertThat(notifications(room, "MEETUP_VACANCY_OPENED")).isEqualTo(1);
		apply(room, attendee, "{}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_REAPPLY_NOT_ALLOWED"));

		Room boundary = room(host, 2, NOW.plus(Duration.ofHours(24)), null, false);
		long boundaryId = applicationId(apply(boundary, attendee, "{}"));
		action(APPLICATION, "/accept", boundaryId, host).andExpect(status().isOk());
		action(MEETUP, "/cancel-participation", boundary.meetupId(), attendee).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("MEETUP_PARTICIPATION_CANCEL_NOT_ALLOWED"));
		assertThat(state(boundaryId)).isEqualTo("CONFIRMED");
	}

	@Test
	void 거절은_주최자만_할_수_있고_같은_요청을_반복해도_알림은_한_번이다() throws Exception {
		Member host = onboard("거절주최자");
		Member applicant = onboard("거절신청자");
		Room room = room(host, 4, START, null, false);
		long id = applicationId(apply(room, applicant, "{}"));
		action(APPLICATION, "/reject", id, applicant).andExpect(status().isForbidden());
		for (int call = 0; call < 2; call++) {
			action(APPLICATION, "/reject", id, host).andExpect(status().isOk());
		}
		action(APPLICATION, "/accept", id, host).andExpect(status().isNotFound());
		assertThat(state(id)).isEqualTo("REJECTED");
		assertThat(notifications(room, "MEETUP_APPLICATION_REJECTED")).isEqualTo(1);
	}

	@Test
	void 마감_전_대기자만_팝업에_세고_놓친_마감은_나중에_한_번만_처리한다() throws Exception {
		Member host = onboard("마감주최자");
		Member applicant = onboard("마감대기자");
		Room open = room(host, 4, START, null, false);
		Room expired = room(host, 4, START, NOW, false);
		Room missed = room(host, 4, NOW.minusSeconds(60), null, false);
		Room deleted = room(host, 4, START, NOW, false);
		Room canceled = room(host, 4, START, NOW, false);
		meetups.findById(deleted.meetupId()).orElseThrow().delete(NOW);
		meetups.findById(canceled.meetupId()).orElseThrow().cancel(NOW);
		meetups.flush();
		long openId = applicationId(apply(open, applicant, "{}"));
		long expiredId = seedPending(expired, applicant);
		long missedId = seedPending(missed, applicant);
		long deletedId = seedPending(deleted, applicant);
		long canceledId = seedPending(canceled, applicant);
		action(APPLICATION, "/accept", expiredId, host).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("MEETUP_APPLICATION_NOT_FOUND"));
		action(MEETUP, "/withdraw-application", expired.meetupId(), applicant).andExpect(status().isNotFound());
		mvc.perform(get(MEETUP + "/applications", expired.meetupId()).cookie(host.session()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.pendingCount").value(0));
		mvc.perform(get("/api/v1/me/notifications/popup").cookie(host.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.meetupRequestCount").value(1));
		assertThat(state(expiredId)).isEqualTo("PENDING");
		deadlines.rejectExpiredApplications(NOW);
		deadlines.rejectExpiredApplications(NOW);
		assertThat(state(expiredId)).isEqualTo("REJECTED");
		assertThat(state(missedId)).isEqualTo("REJECTED");
		assertThat(notifications(expired, "MEETUP_APPLICATION_REJECTED")).isEqualTo(1);
		assertThat(notifications(missed, "MEETUP_APPLICATION_REJECTED")).isEqualTo(1);
		assertThat(List.of(state(openId), state(deletedId), state(canceledId))).containsOnly("PENDING");
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 마지막_한_자리에_동시에_수락해도_한_사람만_확정된다() throws Exception {
		Member host = onboard("동시주최자");
		Member first = onboard("동시신청가");
		Member second = onboard("동시신청나");
		for (int round = 0; round < 3; round++) {
			Room room = room(host, 2, START, null, false);
			long firstId = applicationId(apply(room, first, "{}"));
			long secondId = applicationId(apply(room, second, "{}"));
			assertThat(race(() -> statusOf(action(APPLICATION, "/accept", firstId, host)),
					() -> statusOf(action(APPLICATION, "/accept", secondId, host)))).containsExactlyInAnyOrder(200, 409);
			assertThat(List.of(state(firstId), state(secondId))).containsExactlyInAnyOrder("CONFIRMED", "PENDING");
			assertThat(notifications(room, "MEETUP_APPLICATION_ACCEPTED")).isEqualTo(1);
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 마감_작업과_수락이나_모임_취소가_경합해도_대기_신청을_한_번만_종료한다() throws Exception {
		Member host = onboard("경합주최자");
		Member confirmed = onboard("경합확정자");
		Member pending = onboard("경합대기자");
		Room room = room(host, 4, START, NOW.plusSeconds(60), false);
		long confirmedId = applicationId(apply(room, confirmed, "{}"));
		action(APPLICATION, "/accept", confirmedId, host).andExpect(status().isOk());
		long pendingId = applicationId(apply(room, pending, "{}"));
		List<Integer> results = race(() -> {
			deadlines.rejectExpiredApplications(NOW.plusSeconds(60));
			return 0;
		}, () -> statusOf(action(MEETUP, "/cancel", room.meetupId(), host)));
		assertThat(results).containsExactly(0, 200);
		assertThat(state(pendingId)).isIn("REJECTED", "MEETUP_CANCELED");
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM notification WHERE target_id = ? AND user_id = ?
				  AND type IN ('MEETUP_APPLICATION_REJECTED', 'MEETUP_CANCELED')
				""", Integer.class, room.meetupId(), pending.userId())).isEqualTo(1);
		assertThat(state(confirmedId)).isEqualTo("CONFIRMED");

		Room expired = room(host, 4, START, NOW, false);
		long expiredId = seedPending(expired, pending);
		assertThat(race(() -> {
			deadlines.rejectExpiredApplications(NOW);
			return 0;
		}, () -> statusOf(action(APPLICATION, "/accept", expiredId, host)))).containsExactly(0, 404);
		assertThat(state(expiredId)).isEqualTo("REJECTED");
		assertThat(notifications(expired, "MEETUP_APPLICATION_REJECTED")).isEqualTo(1);
		assertThat(announcements(expired, "JOINED", pending)).isZero();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void 안내_메시지_저장이_실패하면_신청_확정과_대화방_멤버를_롤백한다() throws Exception {
		Member host = onboard("롤백모임장");
		Member applicant = onboard("롤백신청인");
		Room room = room(host, 4, START, null, false);
		long id = applicationId(apply(room, applicant, "{}"));
		doThrow(new DataIntegrityViolationException("test: joined message insert failed")).when(messages).save(any(ChatMessage.class));
		action(APPLICATION, "/accept", id, host).andExpect(status().isInternalServerError());
		assertThat(state(id)).isEqualTo("PENDING");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_room_member WHERE chat_room_id = ? AND user_id = ?",
				Integer.class, room.roomId(), applicant.userId())).isZero();
		assertThat(notifications(room, "MEETUP_APPLICATION_ACCEPTED")).isZero();
	}

	private ResultActions apply(Room room, Member who, String body) throws Exception {
		return mvc.perform(post(MEETUP + "/applications", room.meetupId()).cookie(who.session())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions action(String prefix, String suffix, long id, Member who) throws Exception {
		return mvc.perform(post(prefix + suffix, id).cookie(who.session()));
	}

	private static long applicationId(ResultActions response) throws Exception {
		String body = response.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.data.meetupApplicationId")).longValue();
	}

	private static int statusOf(ResultActions response) {
		return response.andReturn().getResponse().getStatus();
	}

	private static List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(() -> {
				barrier.await(15, TimeUnit.SECONDS);
				return first.call();
			});
			var b = executor.submit(() -> {
				barrier.await(15, TimeUnit.SECONDS);
				return second.call();
			});
			return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
		}
	}

	private Member onboard(String nickname) throws Exception {
		return members.onboard("kan49-" + Integer.toUnsignedString(nickname.hashCode()) + "@example.test", nickname);
	}

	private Room room(Member host, int capacity, Instant startsAt, Instant deadline, boolean messageEnabled) {
		return transactions.execute(status -> {
			MeetupDetails details = new MeetupDetails("신청 테스트", MeetupCategory.STUDY, null, capacity, null,
					messageEnabled, startsAt, null, MeetupMode.ONLINE, null, null, null);
			Long meetupId = meetups.save(new Meetup(host.userId(), details, deadline)).getId();
			return new Room(meetupId, chatRooms.create(meetupId, host.userId(), NOW));
		});
	}

	private long seedPending(Room room, Member applicant) {
		return applications.insertApplication(room.meetupId(), applicant.userId(), null, NOW.minusSeconds(120));
	}

	private String state(long applicationId) {
		return jdbc.queryForObject("SELECT status FROM meetup_application WHERE id = ?", String.class, applicationId);
	}

	private int announcements(Room room, String type, Member actor) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM chat_message WHERE chat_room_id = ? AND type = ? AND sender_id = ?",
				Integer.class, room.roomId(), type, actor.userId());
	}

	private int notifications(Room room, String type) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE target_id = ? AND type = ?",
				Integer.class, room.meetupId(), type);
	}

	private record Room(long meetupId, long roomId) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClock {
		@Bean
		@Primary
		Clock applicationTestClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}

}
