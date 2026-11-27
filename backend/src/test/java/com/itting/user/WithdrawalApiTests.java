package com.itting.user;

import static com.itting.support.GoogleSteps.location;
import static com.itting.support.GoogleSteps.sessionId;
import static com.itting.support.TestUsers.googleSub;
import static com.itting.support.TestUsers.lastSessionCookieHeader;
import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.itting.auth.domain.GoogleAccount;
import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.auth.service.GoogleVerificationService;
import com.itting.auth.service.SessionService;
import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.FakeGoogle;
import com.itting.support.GoogleSteps;
import com.itting.support.GoogleSteps.Attempt;
import com.itting.support.Members;
import com.itting.support.Members.Member;
import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.NotificationSteps;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.itting.user.repository.NicknameHoldRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/me/withdrawal-check}, {@code POST /api/v1/me/withdraw}. Google 확인은 실제 {@code verify}와 콜백을 불러
 * 받고 Google은 {@link FakeGoogle}이 맡는다. 정리 도중 실패해 모두 되돌려지는 경우와 탈퇴가 대화 요청과 겹치는 경우는 테스트
 * 트랜잭션 없이 실제로 커밋해야 해서 {@code WithdrawalConcurrencyTests}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class WithdrawalApiTests {

	private static final String PASSWORD = Members.PASSWORD;

	private static final String GOOGLE_EMAIL = "dawnwalk@gmail.com";

	private static final String CHECK = "/api/v1/me/withdrawal-check";

	private static final String WITHDRAW = "/api/v1/me/withdraw";

	private static final String HELD_NICKNAME = "맡아둔이름";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Autowired
	OnboardingSteps onboarding;

	@Autowired
	CommunitySteps community;

	@Autowired
	ChatSteps chat;

	@Autowired
	NotificationSteps notifications;

	@Autowired
	FakeGoogle fakeGoogle;

	@Autowired
	MutableClock clock;

	@Autowired
	GoogleVerificationService verifications;

	@Autowired
	NicknameHoldRepository nicknameHolds;

	@Autowired
	SessionService sessions;

	Members members;

	GoogleSteps google;

	Instant now;

	@BeforeEach
	void setUp() {
		now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		clock.setNow(now);
		fakeGoogle.clear();
		onboarding.clearNicknameHolds();
		members = new Members(mockMvc, testUsers, onboarding, jdbcTemplate);
		google = new GoogleSteps(mockMvc, fakeGoogle);
	}

	// GET /api/v1/me/withdrawal-check

	@Test
	void 내가_연_예정_모임이_없으면_탈퇴할_수_있고_목록은_비어_있다() throws Exception {
		member("check-none@example.com", "확인없음");
		Cookie session = community.login(mockMvc, "check-none@example.com");

		check(session)
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": { "canWithdraw": true, "hostingMeetups": [], "isGoogleVerified": false },
						  "error": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 연_예정_모임은_시작이_가까운_순으로_주고_지난_취소_삭제_남의_모임은_뺀다() throws Exception {
		long me = member("check-hosting@example.com", "확인주최");
		long other = member("check-other@example.com", "확인타인");
		Cookie session = community.login(mockMvc, "check-hosting@example.com");
		Instant farStart = now.plus(Duration.ofDays(5));
		Instant nearStart = now.plus(Duration.ofDays(2));
		long far = community.insertMeetup(me, "먼 모임", farStart, 4, null);
		long near = community.insertMeetup(me, "가까운 모임", nearStart, 4, null);
		community.insertMeetup(me, "지난 모임", now.minus(Duration.ofHours(1)), 4, null);
		community.insertMeetup(me, "지금 시작한 모임", now, 4, null);
		community.cancelMeetup(community.insertMeetup(me, "취소한 모임", now.plus(Duration.ofDays(1)), 4, null));
		community.deleteMeetup(community.insertMeetup(me, "삭제한 모임", now.plus(Duration.ofDays(1)), 4, null));
		community.insertMeetup(other, "남의 모임", now.plus(Duration.ofDays(1)), 4, null);

		String body = check(session)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.canWithdraw").value(false))
				.andExpect(jsonPath("$.data.isGoogleVerified").value(false))
				.andExpect(jsonPath("$.data.hostingMeetups.length()").value(2))
				.andExpect(jsonPath("$.data.hostingMeetups[0].id").value(near))
				.andExpect(jsonPath("$.data.hostingMeetups[0].title").value("가까운 모임"))
				.andExpect(jsonPath("$.data.hostingMeetups[1].id").value(far))
				.andExpect(jsonPath("$.data.hostingMeetups[1].title").value("먼 모임"))
				.andReturn().getResponse().getContentAsString();

		assertThat(Instant.parse(JsonPath.read(body, "$.data.hostingMeetups[0].startsAt"))).isEqualTo(nearStart);
		assertThat(Instant.parse(JsonPath.read(body, "$.data.hostingMeetups[1].startsAt"))).isEqualTo(farStart);
	}

	@Test
	void Google로만_가입한_계정은_확인을_받으면_true이고_10분이_지나면_false이며_다른_목적의_확인은_세지_않는다() throws Exception {
		googleMember();
		Cookie session = googleLogin();

		check(session).andExpect(status().isOk()).andExpect(jsonPath("$.data.isGoogleVerified").value(false));

		verifications.save(sessionId(session), GoogleOAuthPurpose.LINK, new GoogleAccount(googleSub(GOOGLE_EMAIL), GOOGLE_EMAIL));
		check(session).andExpect(status().isOk()).andExpect(jsonPath("$.data.isGoogleVerified").value(false));

		verifyWithdraw(session);
		check(session).andExpect(status().isOk()).andExpect(jsonPath("$.data.isGoogleVerified").value(true));

		clock.advance(Duration.ofMinutes(10).plusSeconds(1));
		check(session).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.canWithdraw").value(true))
				.andExpect(jsonPath("$.data.isGoogleVerified").value(false));
	}

	@Test
	void Google_확인은_확인받은_그_세션에서만_true이다() throws Exception {
		googleMember();
		Cookie verified = googleLogin();
		Cookie other = googleLogin();
		verifyWithdraw(verified);

		check(verified).andExpect(jsonPath("$.data.isGoogleVerified").value(true));
		check(other).andExpect(jsonPath("$.data.isGoogleVerified").value(false));
	}

	@Test
	void 비밀번호가_있는_계정은_확인_기록이_있어도_isGoogleVerified가_늘_false이다() throws Exception {
		member("check-password@example.com", "확인비번");
		Cookie session = community.login(mockMvc, "check-password@example.com");
		verifications.save(sessionId(session), GoogleOAuthPurpose.WITHDRAW, new GoogleAccount(googleSub(GOOGLE_EMAIL), GOOGLE_EMAIL));

		check(session).andExpect(status().isOk()).andExpect(jsonPath("$.data.isGoogleVerified").value(false));
	}

	@Test
	void 탈퇴_전_확인은_로그인하지_않으면_401이고_온보딩과_새_학기를_마치지_않은_계정은_403이다() throws Exception {
		check(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		testUsers.createEmailUser("check-new@example.com", PASSWORD);
		Cookie notOnboarded = community.login(mockMvc, "check-new@example.com");
		check(notOnboarded).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));

		Member old = members.onboard("check-term@example.com", "새학기전");
		onboarding.openNewTerm();
		check(old.session()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NEW_TERM_INCOMPLETE"));
	}

	// POST /api/v1/me/withdraw: 성공

	@Test
	void 비밀번호_계정이_탈퇴하면_계정을_비우고_정리하고_세션을_모두_지운다() throws Exception {
		Member me = members.onboard("withdraw-me@example.com", "나가는두더지");
		Member other = members.onboard("withdraw-other@example.com", "남는두더지");
		Member third = members.onboard("withdraw-third@example.com", "제삼두더지");
		testUsers.linkGoogle(me.userId(), GOOGLE_EMAIL);
		testUsers.setMattermostId(me.userId(), "mm-withdraw-me");
		Cookie otherDevice = members.login("withdraw-me@example.com");
		Fixture fixture = seed(me, other, third);
		nicknameHolds.hold(HELD_NICKNAME, me.userId(), Duration.ofMinutes(5));
		assertThat(onboarding.holder(HELD_NICKNAME)).isEqualTo(String.valueOf(me.userId()));
		community.flush();
		List<Probe> erased = erasedProbes(me);
		List<Probe> kept = keptProbes(me, other);
		erased.forEach(probe -> assertThat(probe.count()).as("탈퇴 전 %s", probe.name()).isPositive());
		List<Integer> keptBefore = kept.stream().map(Probe::count).toList();

		MockHttpServletResponse response = withdraw(me.session(), """
				{ "password": "%s", "reason": "COURSE_ENDED" }
				""".formatted(PASSWORD))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT))
				.andReturn().getResponse();
		community.flush();

		// 세션 쿠키를 지우는 헤더가 오고, 그 세션과 다른 기기의 세션은 더 쓸 수 없다. 탈퇴한 계정은 세션이 남아 있어도 요청이
		// 막히고 그때 세션이 지워지므로, 요청을 보내기 전에 세션 저장소에서 이 계정의 세션이 모두 지워졌는지 본다.
		assertThat(lastSessionCookieHeader(response)).startsWith("ITTING_SESSION=;").contains("Max-Age=0");
		assertThat(sessions.isAlive(sessionId(me.session()))).isFalse();
		assertThat(sessions.isAlive(sessionId(otherDevice))).isFalse();
		check(me.session()).andExpect(status().isUnauthorized());
		check(otherDevice)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		withdraw(me.session(), """
				{ "password": "%s" }
				""".formatted(PASSWORD)).andExpect(status().isUnauthorized());

		// 계정은 탈퇴 상태이고 로그인에 쓰는 값은 비었다.
		assertThat(jdbcTemplate.queryForMap("""
				SELECT status, email, password_hash, google_sub, google_email, mattermost_id FROM `user` WHERE id = ?
				""", me.userId()))
				.containsEntry("status", "WITHDRAWN")
				.containsEntry("email", null)
				.containsEntry("password_hash", null)
				.containsEntry("google_sub", null)
				.containsEntry("google_email", null)
				.containsEntry("mattermost_id", null);
		Map<String, Object> record = jdbcTemplate.queryForMap(
				"SELECT reason, created_at FROM user_withdrawal WHERE user_id = ?", me.userId());
		assertThat(record).containsEntry("reason", "COURSE_ENDED");
		Instant withdrawnAt = ((LocalDateTime) record.get("created_at")).toInstant(ZoneOffset.UTC);
		assertThat(Duration.between(withdrawnAt, now).abs()).isLessThan(Duration.ofMinutes(1));
		assertThat(count("SELECT COUNT(*) FROM user_withdrawal")).isEqualTo(1);

		// 지우는 것은 모두 지웠고, 남기는 것은 그대로다.
		erased.forEach(probe -> assertThat(probe.count()).as("탈퇴 뒤 %s", probe.name()).isZero());
		for (int i = 0; i < kept.size(); i++) {
			assertThat(kept.get(i).count()).as("탈퇴 뒤 %s", kept.get(i).name()).isEqualTo(keptBefore.get(i));
		}
		assertThat(count("SELECT COUNT(*) FROM `user` WHERE id IN (?, ?)", other.userId(), third.userId())).isEqualTo(2);
		assertThat(onboarding.holder(HELD_NICKNAME)).isNull();

		// 내가 일으킨 남의 알림은 남고, 참여 취소 알림이 주최자에게 둘 늘었다(확정 참여 둘).
		assertThat(count("SELECT COUNT(*) FROM notification WHERE actor_id = ? AND user_id <> ?", me.userId(),
				me.userId())).isEqualTo(fixture.actedNotificationsBefore() + 2);

		// 같은 이메일 · Google · Mattermost 계정으로 다시 가입해도 겹치지 않는다. 같은 닉네임도 쓸 수 있다.
		long again = testUsers.createEmailUser("withdraw-me@example.com", PASSWORD);
		testUsers.linkGoogle(again, GOOGLE_EMAIL);
		testUsers.setMattermostId(again, "mm-withdraw-me");
		assertThat(again).isNotEqualTo(me.userId());
		members.onboard("withdraw-me-again@example.com", "나가는두더지");
	}

	@Test
	void 탈퇴하면_예정_모임의_신청을_정리하고_주최자와_빈자리_구독자에게만_알린다() throws Exception {
		Member me = members.onboard("withdraw-meetup-me@example.com", "모임나감");
		Member host = members.onboard("withdraw-meetup-host@example.com", "모임주최");
		Member subscriber = members.onboard("withdraw-meetup-sub@example.com", "빈자리구독");
		long full = community.insertMeetup(host.userId(), "정원이 찬 모임", now.plus(Duration.ofHours(2)), 2, null);
		long roomy = community.insertMeetup(host.userId(), "자리 남은 모임", now.plus(Duration.ofDays(3)), 4, null);
		long waiting = community.insertMeetup(host.userId(), "기다리는 모임", now.plus(Duration.ofDays(3)), 4, null);
		long closed = community.insertMeetup(host.userId(), "모집 마감 모임", now.plus(Duration.ofDays(3)), 4,
				now.minus(Duration.ofHours(1)));
		long past = community.insertMeetup(host.userId(), "지난 모임", now.minus(Duration.ofHours(1)), 4, null);
		long canceled = community.insertMeetup(host.userId(), "취소된 모임", now.plus(Duration.ofDays(3)), 4, null);
		community.cancelMeetup(canceled);
		community.apply(full, me.userId(), "CONFIRMED");
		community.apply(roomy, me.userId(), "CONFIRMED");
		community.apply(waiting, me.userId(), "PENDING");
		community.apply(closed, me.userId(), "PENDING");
		community.apply(past, me.userId(), "CONFIRMED");
		community.apply(canceled, me.userId(), "CONFIRMED");
		vacancyAlert(full, subscriber.userId());
		vacancyAlert(roomy, subscriber.userId());
		long fullRoom = chat.insertMeetupRoom(full, host.userId(), now.minus(Duration.ofHours(5)));
		chat.join(fullRoom, me.userId(), now.minus(Duration.ofHours(4)));
		community.flush();

		withdraw(me.session(), """
				{ "password": "%s" }
				""".formatted(PASSWORD)).andExpect(status().isOk());
		community.flush();

		// 시작 24시간 전이 지났어도 확정 참여는 취소된다. 기다리던 신청은 모집이 끝났어도 WITHDRAWN이다.
		assertThat(applicationStatus(full, me)).isEqualTo("CANCELED");
		assertThat(applicationStatus(roomy, me)).isEqualTo("CANCELED");
		assertThat(applicationStatus(waiting, me)).isEqualTo("WITHDRAWN");
		assertThat(applicationStatus(closed, me)).isEqualTo("WITHDRAWN");
		// 지난 모임과 취소된 모임의 신청은 그대로다.
		assertThat(applicationStatus(past, me)).isEqualTo("CONFIRMED");
		assertThat(applicationStatus(canceled, me)).isEqualTo("CONFIRMED");

		// 확정 참여마다 주최자가 참여 취소 알림을 받고, 기다리던 신청은 알림이 없다.
		assertThat(notificationsOf(host, "MEETUP_PARTICIPATION_CANCELED", full)).isEqualTo(1);
		assertThat(notificationsOf(host, "MEETUP_PARTICIPATION_CANCELED", roomy)).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM notification WHERE target_id IN (?, ?, ?, ?) AND user_id = ?", waiting,
				closed, past, canceled, host.userId())).isZero();
		assertThat(count("SELECT COUNT(*) FROM notification WHERE actor_id = ? AND user_id = ? AND target_id = ?",
				me.userId(), host.userId(), full)).isEqualTo(1);
		// 정원이 차 있던 모임만 빈자리 알림을 켠 사람에게 빈자리 알림이 간다.
		assertThat(notificationsOf(subscriber, "MEETUP_VACANCY_OPENED", full)).isEqualTo(1);
		assertThat(notificationsOf(subscriber, "MEETUP_VACANCY_OPENED", roomy)).isZero();
		// 모임 대화방에는 "나갔습니다"가 아니라 "탈퇴했습니다" 안내가 하나 들어간다.
		assertThat(chat.leftAt(fullRoom, me.userId())).isNotNull();
		assertThat(messageCount(fullRoom, "WITHDRAWN", me.userId())).isEqualTo(1);
		assertThat(messageCount(fullRoom, "LEFT", me.userId())).isZero();
		assertThat(chat.leftAt(fullRoom, host.userId())).isNull();
	}

	@Test
	void 탈퇴하면_들어_있는_대화방마다_탈퇴_안내를_넣고_나간_방에는_넣지_않는다() throws Exception {
		Member me = members.onboard("withdraw-chat-me@example.com", "대화나감");
		Member other = members.onboard("withdraw-chat-other@example.com", "대화남음");
		Member third = members.onboard("withdraw-chat-third@example.com", "대화제삼");
		long direct = chat.insertDirectRoom(me.userId(), other.userId(), now.minus(Duration.ofHours(3)));
		chat.text(direct, me.userId(), "안녕하세요", now.minus(Duration.ofHours(2)));
		chat.text(direct, other.userId(), "반가워요", now.minus(Duration.ofHours(1)));
		long left = chat.insertDirectRoom(me.userId(), third.userId(), now.minus(Duration.ofHours(3)));
		chat.leave(left, me.userId());
		Object leftBefore = chat.leftAt(left, me.userId());
		long messagesBefore = count("SELECT COUNT(*) FROM chat_message WHERE sender_id = ? AND type = 'TEXT'",
				me.userId());
		community.flush();

		withdraw(me.session(), """
				{ "password": "%s" }
				""".formatted(PASSWORD)).andExpect(status().isOk());
		community.flush();

		assertThat(chat.leftAt(direct, me.userId())).isNotNull();
		assertThat(chat.leftAt(direct, other.userId())).isNull();
		List<Map<String, Object>> messages = chat.messages(direct);
		assertThat(messages).hasSize(3);
		assertThat(messages.getLast()).containsEntry("type", "WITHDRAWN").containsEntry("sender_id", me.userId());
		assertThat(messages.getLast().get("content")).isNull();
		// 이미 나간 방에는 안내도 더하지 않고 나간 시각도 바꾸지 않는다.
		assertThat(chat.messages(left)).isEmpty();
		assertThat(chat.leftAt(left, me.userId())).isEqualTo(leftBefore);
		// 내가 보낸 메시지는 그대로 남는다.
		assertThat(count("SELECT COUNT(*) FROM chat_message WHERE sender_id = ? AND type = 'TEXT'", me.userId()))
				.isEqualTo(messagesBefore);
	}

	@Test
	void 탈퇴하면_보내고_받은_대화_요청을_모두_지우고_남의_요청은_그대로_둔다() throws Exception {
		Member me = members.onboard("withdraw-req-me@example.com", "요청나감");
		Member other = members.onboard("withdraw-req-other@example.com", "요청남음");
		Member third = members.onboard("withdraw-req-third@example.com", "요청제삼");
		Instant sentAt = now.minus(Duration.ofHours(1));
		chat.insertRequest(me.userId(), other.userId(), "보낸 요청", "PENDING", sentAt, null);
		chat.insertRequest(third.userId(), me.userId(), "받은 요청", "PENDING", sentAt, null);
		chat.insertRequest(other.userId(), me.userId(), "거절한 요청", "REJECTED", sentAt, null);
		long between = chat.insertRequest(other.userId(), third.userId(), "남의 요청", "PENDING", sentAt, null);
		community.flush();

		withdraw(me.session(), """
				{ "password": "%s" }
				""".formatted(PASSWORD)).andExpect(status().isOk());
		community.flush();

		assertThat(count("SELECT COUNT(*) FROM chat_request WHERE sender_id = ? OR receiver_id = ?", me.userId(),
				me.userId())).isZero();
		assertThat(chat.requestStatus(between)).isEqualTo("PENDING");
	}

	@Test
	void Google로만_가입한_계정은_확인을_받은_뒤_비밀번호_없이_탈퇴하고_확인_기록은_지워진다() throws Exception {
		long me = googleMember();
		Cookie session = googleLogin();

		withdraw(session, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_VERIFICATION_REQUIRED"));
		verifyWithdraw(session);
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.WITHDRAW)).isPresent();

		MockHttpServletResponse response = withdraw(session, """
				{ "reason": "NO_MATCH" }
				""")
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": null, "error": null }
						""", JsonCompareMode.STRICT))
				.andReturn().getResponse();
		community.flush();

		assertThat(lastSessionCookieHeader(response)).startsWith("ITTING_SESSION=;").contains("Max-Age=0");
		check(session).andExpect(status().isUnauthorized());
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.WITHDRAW)).isEmpty();
		assertThat(jdbcTemplate.queryForMap("SELECT status, google_sub, google_email FROM `user` WHERE id = ?", me))
				.containsEntry("status", "WITHDRAWN")
				.containsEntry("google_sub", null)
				.containsEntry("google_email", null);
		assertThat(jdbcTemplate.queryForObject("SELECT reason FROM user_withdrawal WHERE user_id = ?", String.class, me))
				.isEqualTo("NO_MATCH");
		assertThat(count("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", me)).isZero();
	}

	@Test
	void 탈퇴_사유는_고르지_않아도_된다() throws Exception {
		long me = member("withdraw-noreason@example.com", "사유없음");
		Cookie session = community.login(mockMvc, "withdraw-noreason@example.com");

		withdraw(session, """
				{ "password": "%s", "reason": null }
				""".formatted(PASSWORD)).andExpect(status().isOk());
		community.flush();

		assertThat(jdbcTemplate.queryForMap("SELECT reason FROM user_withdrawal WHERE user_id = ?", me))
				.containsEntry("reason", null);
	}

	// POST /api/v1/me/withdraw: 실패

	@Test
	void 탈퇴_사유가_목록에_없으면_400_INVALID_INPUT이고_아무것도_바뀌지_않는다() throws Exception {
		long me = member("withdraw-bad-reason@example.com", "사유틀림");
		Cookie session = community.login(mockMvc, "withdraw-bad-reason@example.com");
		Map<String, Object> before = accountOf(me);

		withdraw(session, """
				{ "password": "%s", "reason": "BORED" }
				""".formatted(PASSWORD))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("reason"));

		assertStillMember(me, session, before);
	}

	@Test
	void 비밀번호_계정이_비밀번호를_보내지_않으면_400_INVALID_INPUT이다() throws Exception {
		long me = member("withdraw-nopass@example.com", "비번없음");
		Cookie session = community.login(mockMvc, "withdraw-nopass@example.com");
		Map<String, Object> before = accountOf(me);

		withdraw(session, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("password"));
		withdraw(session, "{}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("password"));
		withdraw(session, """
				{ "password": "", "reason": "ETC" }
				""").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("password"));

		assertStillMember(me, session, before);
	}

	@Test
	void 비밀번호가_틀리면_400_CURRENT_PASSWORD_MISMATCH이고_아무것도_바뀌지_않는다() throws Exception {
		long me = member("withdraw-wrong@example.com", "비번틀림");
		Cookie session = community.login(mockMvc, "withdraw-wrong@example.com");
		Map<String, Object> before = accountOf(me);

		withdraw(session, """
				{ "password": "wrong1234!" }
				""").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"));
		// BCrypt가 보는 길이(72바이트)를 넘는 값은 비교하지 않고 틀린 것으로 본다.
		withdraw(session, """
				{ "password": "%s" }
				""".formatted(PASSWORD + "a".repeat(80))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"));

		assertStillMember(me, session, before);
	}

	@Test
	void Google_확인이_없거나_10분이_지났거나_다른_세션의_확인이면_409_GOOGLE_VERIFICATION_REQUIRED이다() throws Exception {
		long me = googleMember();
		Map<String, Object> before = accountOf(me);
		Cookie session = googleLogin();
		Cookie other = googleLogin();

		withdraw(session, null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_VERIFICATION_REQUIRED"));

		// 다른 목적(LINK)의 확인과 다른 세션의 확인은 세지 않는다.
		verifications.save(sessionId(session), GoogleOAuthPurpose.LINK, new GoogleAccount(googleSub(GOOGLE_EMAIL), GOOGLE_EMAIL));
		withdraw(session, null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_VERIFICATION_REQUIRED"));
		verifyWithdraw(other);
		withdraw(session, null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_VERIFICATION_REQUIRED"));

		verifyWithdraw(session);
		clock.advance(Duration.ofMinutes(10).plusSeconds(1));
		withdraw(session, null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("GOOGLE_VERIFICATION_REQUIRED"));

		assertStillMember(me, session, before);
	}

	@Test
	void 내가_연_예정_모임이_있으면_409_HOSTING_MEETUP_EXISTS이고_아무것도_바뀌지_않는다() throws Exception {
		Member me = members.onboard("withdraw-host@example.com", "주최자");
		Member other = members.onboard("withdraw-host-other@example.com", "주최타인");
		community.insertMeetup(me.userId(), "내가 연 모임", now.plus(Duration.ofDays(2)), 4, null);
		long joined = community.insertMeetup(other.userId(), "남의 모임", now.plus(Duration.ofDays(2)), 4, null);
		community.apply(joined, me.userId(), "PENDING");
		long room = chat.insertDirectRoom(me.userId(), other.userId(), now.minus(Duration.ofHours(1)));
		chat.insertRequest(other.userId(), me.userId(), "안녕", "PENDING", now.minus(Duration.ofHours(1)), null);
		Map<String, Object> before = accountOf(me.userId());

		withdraw(me.session(), """
				{ "password": "%s", "reason": "ETC" }
				""".formatted(PASSWORD))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("HOSTING_MEETUP_EXISTS"));
		community.flush();

		assertThat(applicationStatus(joined, me)).isEqualTo("PENDING");
		assertThat(chat.leftAt(room, me.userId())).isNull();
		assertThat(chat.messages(room)).isEmpty();
		assertThat(count("SELECT COUNT(*) FROM chat_request WHERE receiver_id = ?", me.userId())).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM burrow WHERE user_id = ?", me.userId())).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM user_interest WHERE user_id = ?", me.userId())).isPositive();
		assertStillMember(me.userId(), me.session(), before);
	}

	@Test
	void 모임_때문에_막혀도_Google_확인은_남아_모임을_정리하고_다시_눌러_탈퇴할_수_있다() throws Exception {
		long me = googleMember();
		Cookie session = googleLogin();
		long hosting = community.insertMeetup(me, "내가 연 모임", now.plus(Duration.ofDays(2)), 4, null);
		verifyWithdraw(session);

		withdraw(session, null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("HOSTING_MEETUP_EXISTS"));
		assertThat(verifications.find(sessionId(session), GoogleOAuthPurpose.WITHDRAW)).isPresent();
		check(session).andExpect(jsonPath("$.data.canWithdraw").value(false))
				.andExpect(jsonPath("$.data.isGoogleVerified").value(true));

		community.cancelMeetup(hosting);
		withdraw(session, null).andExpect(status().isOk());
		community.flush();

		assertThat(jdbcTemplate.queryForObject("SELECT status FROM `user` WHERE id = ?", String.class, me))
				.isEqualTo("WITHDRAWN");
	}

	@Test
	void 탈퇴는_로그인하지_않으면_401이고_온보딩과_새_학기를_마치지_않은_계정은_403이다() throws Exception {
		withdraw(null, """
				{ "password": "%s" }
				""".formatted(PASSWORD))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		long notOnboarded = testUsers.createEmailUser("withdraw-new@example.com", PASSWORD);
		Cookie newcomer = community.login(mockMvc, "withdraw-new@example.com");
		withdraw(newcomer, """
				{ "password": "%s" }
				""".formatted(PASSWORD))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));

		Member old = members.onboard("withdraw-term@example.com", "새학기전");
		onboarding.openNewTerm();
		withdraw(old.session(), """
				{ "password": "%s" }
				""".formatted(PASSWORD))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NEW_TERM_INCOMPLETE"));

		community.flush();
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM `user` WHERE id = ?", String.class, notOnboarded))
				.isEqualTo("ACTIVE");
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM `user` WHERE id = ?", String.class, old.userId()))
				.isEqualTo("ACTIVE");
		assertThat(count("SELECT COUNT(*) FROM user_withdrawal")).isZero();
	}

	// ---- 준비 · 확인 도구 ----

	/**
	 * 탈퇴하는 사람({@code me})이 남긴 흔적을 모든 영역에 만든다. 지우는 표의 테이블마다 줄이 있고, 남기는 것(글, 댓글, 쓴 방명록,
	 * 채팅 메시지, 남이 받은 내 알림, 신고)도 있다. 정원이 찬 모임 · 자리가 남은 모임의 확정 참여와 기다리는 신청도 만든다.
	 */
	private Fixture seed(Member me, Member other, Member third) {
		Instant earlier = now.minus(Duration.ofHours(2));
		// 커뮤니티: 남의 글과 댓글에 한 좋아요 · 저장, 내가 쓴 글 · 댓글(남는다)
		long otherPost = community.insertPost(other.userId(), "FREE", "남의 글", "본문", earlier);
		long otherComment = community.comment(otherPost, other.userId(), null, "PUBLIC");
		community.like(otherPost, me.userId(), earlier);
		community.bookmark(otherPost, me.userId());
		community.commentLike(otherComment, me.userId());
		long myPost = community.insertPost(me.userId(), "FREE", "내 글", "본문", earlier);
		community.comment(myPost, me.userId(), null, "PUBLIC");
		community.insertReport(me.userId(), "POST", otherPost);
		// 땅굴: 단골(양쪽), 내 땅굴이 받은 방명록(지운다), 내가 남의 땅굴에 쓴 방명록(남긴다)
		jdbcTemplate.update("INSERT INTO regular_burrow (user_id, burrow_id, created_at) VALUES (?, ?, UTC_TIMESTAMP(6))",
				me.userId(), other.burrowId());
		jdbcTemplate.update("INSERT INTO regular_burrow (user_id, burrow_id, created_at) VALUES (?, ?, UTC_TIMESTAMP(6))",
				other.userId(), me.burrowId());
		members.writeGuestbook(me.burrowId(), other.userId(), "내 땅굴에 남긴 글", earlier);
		members.writeGuestbook(other.burrowId(), me.userId(), "남의 땅굴에 남긴 글", earlier);
		// 알림: 내가 받은 것(지운다), 내가 일으킨 남의 것(남긴다), 설정, 푸시 등록
		notifications.insertPostCommented(me.userId(), other.userId(), otherPost, earlier, null);
		notifications.insertPostCommented(other.userId(), me.userId(), myPost, earlier, null);
		notifications.setting(me.userId(), true, false, true, true, true);
		jdbcTemplate.update("""
				INSERT INTO push_subscription (user_id, endpoint, p256dh, auth, created_at)
				VALUES (?, 'https://push.example.test/withdraw-me', 'p256dh-key', 'auth-key', UTC_TIMESTAMP(6))
				""", me.userId());
		// 오늘의 추천에 내가 뽑힌 기록(남긴다)
		jdbcTemplate.update("""
				INSERT INTO daily_recommendation (user_id, recommended_on, created_at)
				VALUES (?, UTC_DATE(), UTC_TIMESTAMP(6))
				""", other.userId());
		jdbcTemplate.update("""
				INSERT INTO daily_recommendation_member (daily_recommendation_id, sort_order, target_user_id)
				SELECT id, 1, ? FROM daily_recommendation WHERE user_id = ?
				""", me.userId(), other.userId());
		// 대화: 1:1 방, 대화 요청(보낸 것 · 받은 것)
		long room = chat.insertDirectRoom(me.userId(), other.userId(), earlier);
		chat.text(room, me.userId(), "안녕하세요", earlier);
		chat.insertRequest(me.userId(), third.userId(), "보낸 요청", "PENDING", earlier, null);
		chat.insertRequest(third.userId(), me.userId(), "받은 요청", "PENDING", earlier, null);
		// 모임: 정원이 찬 모임과 자리가 남은 모임의 확정 참여, 기다리는 신청, 찜
		long full = community.insertMeetup(other.userId(), "정원이 찬 모임", now.plus(Duration.ofHours(2)), 2, null);
		long roomy = community.insertMeetup(other.userId(), "자리 남은 모임", now.plus(Duration.ofDays(3)), 4, null);
		long waiting = community.insertMeetup(other.userId(), "기다리는 모임", now.plus(Duration.ofDays(3)), 4, null);
		community.apply(full, me.userId(), "CONFIRMED");
		community.apply(roomy, me.userId(), "CONFIRMED");
		community.apply(waiting, me.userId(), "PENDING");
		vacancyAlert(full, me.userId());
		vacancyAlert(full, third.userId());
		long meetupRoom = chat.insertMeetupRoom(full, other.userId(), earlier);
		chat.join(meetupRoom, me.userId(), earlier);
		int acted = count("SELECT COUNT(*) FROM notification WHERE actor_id = ? AND user_id <> ?", me.userId(),
				me.userId());
		return new Fixture(acted);
	}

	private record Fixture(int actedNotificationsBefore) {
	}

	/** 탈퇴하면 이 사람의 줄이 모두 사라져야 하는 곳. */
	private List<Probe> erasedProbes(Member me) {
		long user = me.userId();
		long burrow = me.burrowId();
		return List.of(
				new Probe("user_profile", "SELECT COUNT(*) FROM user_profile WHERE user_id = ?", user),
				new Probe("user_interest", "SELECT COUNT(*) FROM user_interest WHERE user_id = ?", user),
				new Probe("user_answer", "SELECT COUNT(*) FROM user_answer WHERE user_id = ?", user),
				new Probe("burrow", "SELECT COUNT(*) FROM burrow WHERE user_id = ?", user),
				new Probe("burrow_entrance", "SELECT COUNT(*) FROM burrow_entrance WHERE burrow_id = ?", burrow),
				new Probe("guestbook_entry(받은 것)", "SELECT COUNT(*) FROM guestbook_entry WHERE burrow_id = ?", burrow),
				new Probe("regular_burrow", "SELECT COUNT(*) FROM regular_burrow WHERE user_id = ? OR burrow_id = ?", user,
						burrow),
				new Probe("chat_request", "SELECT COUNT(*) FROM chat_request WHERE sender_id = ? OR receiver_id = ?", user,
						user),
				new Probe("meetup_like", "SELECT COUNT(*) FROM meetup_like WHERE user_id = ?", user),
				new Probe("post_like", "SELECT COUNT(*) FROM post_like WHERE user_id = ?", user),
				new Probe("comment_like", "SELECT COUNT(*) FROM comment_like WHERE user_id = ?", user),
				new Probe("post_bookmark", "SELECT COUNT(*) FROM post_bookmark WHERE user_id = ?", user),
				new Probe("notification_setting", "SELECT COUNT(*) FROM notification_setting WHERE user_id = ?", user),
				new Probe("push_subscription", "SELECT COUNT(*) FROM push_subscription WHERE user_id = ?", user),
				new Probe("notification(받은 것)", "SELECT COUNT(*) FROM notification WHERE user_id = ?", user));
	}

	/** 탈퇴해도 줄 수가 그대로여야 하는 곳. */
	private List<Probe> keptProbes(Member me, Member other) {
		long user = me.userId();
		return List.of(
				new Probe("post(쓴 글)", "SELECT COUNT(*) FROM post WHERE author_id = ?", user),
				new Probe("comment(쓴 댓글)", "SELECT COUNT(*) FROM comment WHERE author_id = ?", user),
				new Probe("guestbook_entry(쓴 것)", "SELECT COUNT(*) FROM guestbook_entry WHERE author_id = ?", user),
				new Probe("chat_message(쓴 것)", "SELECT COUNT(*) FROM chat_message WHERE sender_id = ? AND type = 'TEXT'",
						user),
				new Probe("report(낸 신고)", "SELECT COUNT(*) FROM report WHERE reporter_id = ?", user),
				new Probe("daily_recommendation_member(뽑힌 기록)",
						"SELECT COUNT(*) FROM daily_recommendation_member WHERE target_user_id = ?", user),
				new Probe("남의 땅굴", "SELECT COUNT(*) FROM burrow WHERE user_id = ?", other.userId()),
				new Probe("남의 받은 방명록", "SELECT COUNT(*) FROM guestbook_entry WHERE burrow_id = ?", other.burrowId()));
	}

	/** 줄 수를 세는 질문 하나. 부를 때마다 지금의 줄 수를 센다. */
	private final class Probe {

		private final String name;

		private final String sql;

		private final Object[] args;

		private Probe(String name, String sql, Object... args) {
			this.name = name;
			this.sql = sql;
			this.args = args;
		}

		String name() {
			return name;
		}

		int count() {
			return WithdrawalApiTests.this.count(sql, args);
		}

	}

	/** 상태와 로그인에 쓰는 값. 실패한 탈퇴가 아무것도 바꾸지 않았는지 앞뒤로 견준다. */
	private Map<String, Object> accountOf(long userId) {
		community.flush();
		return jdbcTemplate.queryForMap("""
				SELECT status, email, password_hash, google_sub, google_email, mattermost_id FROM `user` WHERE id = ?
				""", userId);
	}

	/** 탈퇴가 막혀도 계정, 프로필, 탈퇴 기록 없음, 로그인 세션이 그대로다. */
	private void assertStillMember(long userId, Cookie session, Map<String, Object> accountBefore) throws Exception {
		assertThat(accountOf(userId)).isEqualTo(accountBefore).containsEntry("status", "ACTIVE");
		assertThat(count("SELECT COUNT(*) FROM user_withdrawal WHERE user_id = ?", userId)).isZero();
		assertThat(count("SELECT COUNT(*) FROM user_profile WHERE user_id = ?", userId)).isEqualTo(1);
		check(session).andExpect(status().isOk());
	}

	private ResultActions check(Cookie session) throws Exception {
		var request = get(CHECK);
		return mockMvc.perform(session == null ? request : request.cookie(session));
	}

	/** 본문이 null이면 본문 없이 부른다. */
	private ResultActions withdraw(Cookie session, String body) throws Exception {
		var request = post(WITHDRAW);
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(session == null ? request : request.cookie(session));
	}

	/** 온보딩을 마치고 닉네임이 있는 비밀번호 계정(땅굴 없음). */
	private long member(String email, String nickname) {
		return community.createMember(email, nickname);
	}

	/** Google로만 가입하고 온보딩을 마친 계정(땅굴 없음). */
	private long googleMember() {
		long userId = testUsers.createGoogleOnlyUser(GOOGLE_EMAIL);
		testUsers.markOnboarded(userId);
		return userId;
	}

	/** 가짜 Google로 로그인한 세션. 부를 때마다 새 세션이다. */
	private Cookie googleLogin() throws Exception {
		return sessionCookie(google.loginWith(googleSub(GOOGLE_EMAIL), GOOGLE_EMAIL)
				.andExpect(status().isFound())
				.andReturn().getResponse());
	}

	/** 이 세션에서 탈퇴를 위한 Google 확인을 마친다. */
	private void verifyWithdraw(Cookie session) throws Exception {
		Attempt attempt = google.startVerify(session, "WITHDRAW");
		MockHttpServletResponse response = google
				.callback(attempt, google.codeFor(attempt, googleSub(GOOGLE_EMAIL), GOOGLE_EMAIL), session)
				.andExpect(status().isFound())
				.andReturn().getResponse();
		assertThat(location(response)).isEqualTo("http://localhost:3000/settings/leave");
	}

	private void vacancyAlert(long meetupId, long userId) {
		jdbcTemplate.update("""
				INSERT INTO meetup_like (meetup_id, user_id, vacancy_alert, created_at, updated_at)
				VALUES (?, ?, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", meetupId, userId);
	}

	private String applicationStatus(long meetupId, Member member) {
		return jdbcTemplate.queryForObject("SELECT status FROM meetup_application WHERE meetup_id = ? AND user_id = ?",
				String.class, meetupId, member.userId());
	}

	private int notificationsOf(Member receiver, String type, long meetupId) {
		return count("SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = ? AND target_id = ?",
				receiver.userId(), type, meetupId);
	}

	private int messageCount(long roomId, String type, long senderId) {
		return count("SELECT COUNT(*) FROM chat_message WHERE chat_room_id = ? AND type = ? AND sender_id = ?", roomId,
				type, senderId);
	}

	private int count(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}

}
