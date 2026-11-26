package com.itting.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.itting.auth.service.SessionService;
import com.itting.realtime.service.EventStreamBus;
import com.itting.realtime.service.EventStreamConnections;
import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.EventStreamClient;
import com.itting.support.EventStreamMarker;
import com.itting.support.RealServer;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실시간 소식 연결({@code GET /api/v1/me/event-stream})을 열고 끊는 일. 연결은 열어 둔 채로 흘러오는 것을 봐야 해서 서버를 실제
 * 포트로 띄우고 HTTP로 부른다. 서버가 다른 스레드에서 돌아 테스트 트랜잭션을 쓸 수 없으므로 넣은 줄은 끝나고 지운다.
 *
 * <p>소식의 값은 영역의 테스트가 본다({@code ChatEventStreamTests}, {@code NotificationEventStreamTests}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class EventStreamApiTests {

	private static final String PASSWORD = CommunitySteps.PASSWORD;

	private static final String UNREAD_CHANGED = "NOTIFICATION_UNREAD_CHANGED";

	@LocalServerPort
	int port;

	@Autowired
	TestUsers testUsers;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	EventStreamConnections connections;

	@Autowired
	SessionService sessionService;

	@Autowired
	EventStreamMarker marker;

	RealServer server;

	private final List<Long> userIds = new ArrayList<>();

	private final List<EventStreamClient> streams = new ArrayList<>();

	private Long oldTermId;

	@BeforeEach
	void setUp() {
		server = new RealServer(port);
	}

	@AfterEach
	void cleanUp() {
		EventStreamClient.closeAll(streams, connections);
		for (Long userId : userIds) {
			jdbcTemplate.update("""
					DELETE FROM burrow_entrance WHERE burrow_id IN (SELECT id FROM burrow WHERE user_id = ?)
					""", userId);
			jdbcTemplate.update("DELETE FROM burrow WHERE user_id = ?", userId);
		}
		if (!userIds.isEmpty()) {
			chat.deleteAllOf(userIds.stream().mapToLong(Long::longValue).toArray());
		}
		if (oldTermId != null) {
			jdbcTemplate.update("DELETE FROM term WHERE id = ?", oldTermId);
		}
	}

	@Test
	void 연결하면_event_stream으로_열리고_바로_신호가_온다() throws Exception {
		long me = member("stream-open@example.com", "연결나");

		EventStreamClient stream = open(server.login("stream-open@example.com", PASSWORD));

		assertThat(stream.status()).isEqualTo(200);
		assertThat(stream.contentType()).startsWith("text/event-stream");
		stream.awaitPings(1);
		assertThat(stream.raw()).startsWith(":ping\n\n");
		assertThat(stream.pending()).isEmpty();
		assertThat(connections.countOf(me)).isEqualTo(1);
	}

	@Test
	void 로그인하지_않으면_401을_응답_틀로_준다() throws Exception {
		EventStreamClient stream = open(null);

		assertThat(stream.status()).isEqualTo(401);
		assertThat(stream.contentType()).startsWith("application/json");
		String body = stream.body();
		assertThat(JsonPath.<Boolean>read(body, "$.success")).isFalse();
		assertThat(JsonPath.<String>read(body, "$.error.code")).isEqualTo("UNAUTHENTICATED");
		assertThat(JsonPath.<String>read(body, "$.error.message")).isEqualTo("로그인이 필요합니다.");
	}

	@Test
	void 온보딩을_마치지_않은_계정은_403_ONBOARDING_INCOMPLETE를_응답_틀로_준다() throws Exception {
		long userId = testUsers.createEmailUser("stream-onboarding@example.com", PASSWORD);
		userIds.add(userId);

		EventStreamClient stream = open(server.login("stream-onboarding@example.com", PASSWORD));

		assertThat(stream.status()).isEqualTo(403);
		assertThat(stream.contentType()).startsWith("application/json");
		String body = stream.body();
		assertThat(JsonPath.<Boolean>read(body, "$.success")).isFalse();
		assertThat(JsonPath.<String>read(body, "$.error.code")).isEqualTo("ONBOARDING_INCOMPLETE");
		assertThat(connections.countOf(userId)).isZero();
	}

	@Test
	void 새_학기를_고르지_않은_계정도_연결된다() throws Exception {
		long me = member("stream-new-term@example.com", "새학기나");
		moveBurrowToOldTerm(me);
		String cookie = server.login("stream-new-term@example.com", PASSWORD);
		assertThat(JsonPath.<String>read(server.get("/api/v1/me", cookie).body(), "$.data.nextStep"))
				.isEqualTo("NEW_TERM");
		HttpResponse<String> blocked = server.get("/api/v1/me/chat-rooms", cookie);
		assertThat(blocked.statusCode()).isEqualTo(403);
		assertThat(JsonPath.<String>read(blocked.body(), "$.error.code")).isEqualTo("NEW_TERM_INCOMPLETE");

		EventStreamClient stream = open(cookie);

		assertThat(stream.status()).isEqualTo(200);
		stream.awaitPings(1);
		assertThat(connections.countOf(me)).isEqualTo(1);
	}

	@Test
	void 연결을_유지하는_신호가_되풀이해_온다() throws Exception {
		member("stream-ping@example.com", "신호나");
		EventStreamClient stream = open(server.login("stream-ping@example.com", PASSWORD));
		stream.awaitPings(1);

		connections.heartbeat();
		connections.heartbeat();

		stream.awaitPings(3);
		// 신호만 흘렀다. 예약된 신호가 그 사이에 한 번 더 올 수 있어 개수는 정하지 않는다.
		assertThat(stream.raw()).matches("(:ping\n\n){3,}");
		assertThat(stream.pending()).isEmpty();
		assertThat(stream.isEnded()).isFalse();
	}

	@Test
	void 예약된_신호가_스스로_온다() throws Exception {
		member("stream-scheduled@example.com", "예약나");
		EventStreamClient stream = open(server.login("stream-scheduled@example.com", PASSWORD));
		stream.awaitPings(1);

		// 아무것도 부르지 않고 기다린다. 설정한 간격(25초) 안에 예약 작업이 신호를 보낸다.
		stream.awaitPings(2, Duration.ofSeconds(40));

		assertThat(stream.raw()).matches("(:ping\n\n){2,}");
		assertThat(stream.isEnded()).isFalse();
	}

	@Test
	void 세션이_지워졌다는_알림을_놓쳐도_다음_신호_때_연결이_끊긴다() throws Exception {
		long me = member("stream-missed@example.com", "놓친나");
		String cookie = server.login("stream-missed@example.com", PASSWORD);
		EventStreamClient stream = open(cookie);
		stream.awaitPings(1);
		String sessionId = new String(Base64.getDecoder().decode(cookie.substring(cookie.indexOf('=') + 1)),
				StandardCharsets.UTF_8);
		assertThat(sessionService.isAlive(sessionId)).isTrue();

		// 세션의 값만 지운다. Spring Session은 이 키가 지워진 것으로는 "세션이 지워졌다"는 이벤트를 내지 않는다(알림을 놓친 것과 같다).
		redis.delete("spring:session:sessions:" + sessionId);

		assertThat(sessionService.isAlive(sessionId)).isFalse();
		marker.mark(me);
		stream.await(EventStreamMarker.NAME);
		assertThat(stream.isEnded()).isFalse();

		connections.heartbeat();

		assertThat(stream.awaitEnded()).isTrue();
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(connections.isConnected(me)).isFalse());
		assertThat(open(cookie).status()).isEqualTo(401);
	}

	@Test
	void 브라우저가_끊은_연결은_신호를_보내다_지워진다() throws Exception {
		long me = member("stream-gone@example.com", "끊긴나");
		EventStreamClient stream = open(server.login("stream-gone@example.com", PASSWORD));
		stream.awaitPings(1);
		assertThat(connections.countOf(me)).isEqualTo(1);

		stream.close();

		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
			connections.heartbeat();
			assertThat(connections.countOf(me)).isZero();
		});
		assertThat(connections.isConnected(me)).isFalse();
	}

	@Test
	void 로그아웃하면_그_세션의_연결만_끊긴다() throws Exception {
		long me = member("stream-logout@example.com", "나가는나");
		long other = member("stream-logout-other@example.com", "남는너");
		String phone = server.login("stream-logout@example.com", PASSWORD);
		String laptop = server.login("stream-logout@example.com", PASSWORD);
		EventStreamClient phoneStream = open(phone);
		EventStreamClient laptopStream = open(laptop);
		EventStreamClient otherStream = open(server.login("stream-logout-other@example.com", PASSWORD));
		phoneStream.awaitPings(1);
		laptopStream.awaitPings(1);
		otherStream.awaitPings(1);

		assertThat(server.send("DELETE", "/api/v1/sessions/current", phone, null).statusCode()).isEqualTo(200);

		assertThat(phoneStream.awaitEnded()).isTrue();
		// 끊을 때 에러를 보내지 않는다. 연결 위로는 신호만 흘렀다.
		assertThat(phoneStream.raw()).matches("(:ping\n\n)+");
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(connections.countOf(me)).isEqualTo(1));
		connections.heartbeat();
		laptopStream.awaitPings(2);
		otherStream.awaitPings(2);
		assertThat(laptopStream.isEnded()).isFalse();
		assertThat(otherStream.isEnded()).isFalse();
		assertThat(connections.countOf(other)).isEqualTo(1);
		// 로그아웃한 세션으로는 다시 연결할 수 없다.
		assertThat(open(phone).status()).isEqualTo(401);
	}

	@Test
	void 비밀번호를_바꾸면_다른_기기의_연결만_끊긴다() throws Exception {
		long me = member("stream-password@example.com", "바꾸는나");
		String here = server.login("stream-password@example.com", PASSWORD);
		String elsewhere = server.login("stream-password@example.com", PASSWORD);
		EventStreamClient hereStream = open(here);
		EventStreamClient elsewhereStream = open(elsewhere);
		hereStream.awaitPings(1);
		elsewhereStream.awaitPings(1);

		HttpResponse<String> changed = server.send("PATCH", "/api/v1/me/password", here, """
				{ "currentPassword": "%s", "newPassword": "newmole5678@" }
				""".formatted(PASSWORD));

		assertThat(changed.statusCode()).isEqualTo(200);
		assertThat(elsewhereStream.awaitEnded()).isTrue();
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(connections.countOf(me)).isEqualTo(1));
		connections.heartbeat();
		hereStream.awaitPings(2);
		assertThat(hereStream.isEnded()).isFalse();
	}

	@Test
	void 계정의_세션을_모두_지우면_그_계정의_연결이_모두_끊긴다() throws Exception {
		long me = member("stream-end-all@example.com", "모두나");
		member("stream-end-all-other@example.com", "남는너");
		EventStreamClient first = open(server.login("stream-end-all@example.com", PASSWORD));
		EventStreamClient second = open(server.login("stream-end-all@example.com", PASSWORD));
		EventStreamClient otherStream = open(server.login("stream-end-all-other@example.com", PASSWORD));
		first.awaitPings(1);
		second.awaitPings(1);
		otherStream.awaitPings(1);

		// 정지, 탈퇴, 비밀번호 재설정이 부르는 것
		sessionService.endAll(me);

		assertThat(first.awaitEnded()).isTrue();
		assertThat(second.awaitEnded()).isTrue();
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(connections.isConnected(me)).isFalse());
		connections.heartbeat();
		otherStream.awaitPings(2);
		assertThat(otherStream.isEnded()).isFalse();
	}

	@Test
	void 다른_서버가_Redis에_실은_신호도_이_서버의_연결로_간다() throws Exception {
		long me = member("stream-redis@example.com", "레디스나");
		EventStreamClient stream = open(server.login("stream-redis@example.com", PASSWORD));
		stream.awaitPings(1);

		redis.convertAndSend(EventStreamBus.CHANNEL, signal(UNREAD_CHANGED, me));

		assertThat(stream.await(UNREAD_CHANGED).data()).isEqualTo("{\"unreadCount\":0,\"hasUnread\":false}");
		assertThat(stream.pending()).isEmpty();
	}

	@Test
	void 읽을_수_없는_신호는_버리고_다음_신호를_계속_처리한다() throws Exception {
		long me = member("stream-broken@example.com", "버리는나");
		EventStreamClient stream = open(server.login("stream-broken@example.com", PASSWORD));
		stream.awaitPings(1);

		redis.convertAndSend(EventStreamBus.CHANNEL, "JSON이 아닌 글");
		redis.convertAndSend(EventStreamBus.CHANNEL, "{\"type\":\"NO_SUCH_SIGNAL\",\"args\":{\"userId\":" + me + "}}");
		redis.convertAndSend(EventStreamBus.CHANNEL, "{\"type\":\"CHAT_MESSAGE_CREATED\",\"args\":{}}");
		redis.convertAndSend(EventStreamBus.CHANNEL, "{\"type\":\"" + UNREAD_CHANGED + "\"}");
		redis.convertAndSend(EventStreamBus.CHANNEL, signal(UNREAD_CHANGED, me));

		stream.await(UNREAD_CHANGED);
		assertThat(stream.pending()).isEmpty();
		assertThat(stream.isEnded()).isFalse();
	}

	/** 온보딩을 마친 계정. 땅굴이 없어 새 학기 고르기에도 막히지 않는다. */
	private long member(String email, String nickname) {
		long userId = steps.createMember(email, nickname);
		userIds.add(userId);
		return userId;
	}

	/** 지금 학기보다 먼저 열린 학기의 들판에 이 계정의 땅굴을 둔다. 이 계정만 새 학기를 고르지 않은 상태가 된다. */
	private void moveBurrowToOldTerm(long userId) {
		jdbcTemplate.update("""
				INSERT INTO term (year, half, opened_at, created_at, updated_at)
				VALUES (2020, 1, '2020-01-01 00:00:00', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""");
		oldTermId = jdbcTemplate.queryForObject("SELECT id FROM term WHERE year = 2020 AND half = 1", Long.class);
		long burrowId = steps.insertBurrow(userId);
		jdbcTemplate.update("""
				INSERT INTO burrow_entrance (burrow_id, term_id, campus_floor_id, slot, created_at, updated_at)
				VALUES (?, ?, ?, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", burrowId, oldTermId, testUsers.seoulFloorId(2));
	}

	private EventStreamClient open(String cookie) throws Exception {
		EventStreamClient stream = server.eventStream(cookie);
		streams.add(stream);
		return stream;
	}

	private static String signal(String type, long userId) {
		return "{\"type\":\"" + type + "\",\"args\":{\"userId\":" + userId + "}}";
	}

}
