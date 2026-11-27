package com.itting.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.itting.realtime.service.EventStreamConnections;
import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.EventStreamClient;
import com.itting.support.EventStreamMarker;
import com.itting.support.RealServer;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 알림의 실시간 소식 {@code NOTIFICATION_UNREAD_CHANGED}가 열어 둔 연결({@code GET /api/v1/me/event-stream})로 오는지 본다.
 * 알림은 채팅 메시지를 보내 만든다(받는 사람에게 새 메시지 알림이 생긴다). 서버를 실제 포트로 띄워 HTTP로 부르므로 넣은 줄은
 * 끝나고 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class NotificationEventStreamTests {

	private static final String PASSWORD = CommunitySteps.PASSWORD;

	private static final String UNREAD_CHANGED = "NOTIFICATION_UNREAD_CHANGED";

	@LocalServerPort
	int port;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	EventStreamMarker marker;

	@Autowired
	EventStreamConnections connections;

	@Autowired
	JdbcTemplate jdbcTemplate;

	RealServer server;

	long sender;

	long receiver;

	long room;

	String senderCookie;

	String receiverCookie;

	private final List<EventStreamClient> streams = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		server = new RealServer(port);
		sender = steps.createMember("notice-stream-sender@example.com", "알림보냄");
		receiver = steps.createMember("notice-stream-receiver@example.com", "알림받음");
		room = chat.insertDirectRoom(sender, receiver, Instant.now());
		senderCookie = server.login("notice-stream-sender@example.com", PASSWORD);
		receiverCookie = server.login("notice-stream-receiver@example.com", PASSWORD);
	}

	@AfterEach
	void cleanUp() {
		EventStreamClient.closeAll(streams, connections);
		jdbcTemplate.update("DELETE FROM notification_setting WHERE user_id IN (?, ?)", sender, receiver);
		chat.deleteAllOf(sender, receiver);
	}

	@Test
	void 알림이_생기면_받는_사람에게_안_읽은_수와_빨간_점이_온다() throws Exception {
		EventStreamClient senders = open(senderCookie);
		EventStreamClient receivers = open(receiverCookie);

		sendMessage("안녕하세요");

		assertThat(receivers.await(UNREAD_CHANGED).data()).isEqualTo("{\"unreadCount\":1,\"hasUnread\":true}");
		// 알림을 일으킨 사람에게는 오지 않는다.
		marker.mark(sender);
		senders.await(EventStreamMarker.NAME);
		assertThat(senders.pending()).noneMatch(event -> event.name().equals(UNREAD_CHANGED));
	}

	@Test
	void 모두_읽으면_0이_오고_그_계정의_다른_연결도_받는다() throws Exception {
		EventStreamClient tab = open(receiverCookie);
		EventStreamClient phone = open(server.login("notice-stream-receiver@example.com", PASSWORD));
		sendMessage("안녕하세요");
		assertThat(tab.await(UNREAD_CHANGED).data()).isEqualTo("{\"unreadCount\":1,\"hasUnread\":true}");
		assertThat(phone.await(UNREAD_CHANGED).data()).isEqualTo("{\"unreadCount\":1,\"hasUnread\":true}");

		HttpResponse<String> read = server.post("/api/v1/me/notifications/read-all", receiverCookie, null);

		assertThat(read.statusCode()).isEqualTo(200);
		// 다른 탭에서 읽어도 이 소식이 와서 탭끼리 맞는다.
		assertThat(tab.await(UNREAD_CHANGED).data()).isEqualTo("{\"unreadCount\":0,\"hasUnread\":false}");
		assertThat(phone.await(UNREAD_CHANGED).data()).isEqualTo("{\"unreadCount\":0,\"hasUnread\":false}");
	}

	@Test
	void 알림_설정을_바꿔_빨간_점이_달라지면_소식이_온다() throws Exception {
		EventStreamClient receivers = open(receiverCookie);
		sendMessage("안녕하세요");
		assertThat(receivers.await(UNREAD_CHANGED).data()).isEqualTo("{\"unreadCount\":1,\"hasUnread\":true}");

		HttpResponse<String> changed = server.send("PATCH", "/api/v1/me/notification-settings", receiverCookie, """
				{ "isMeetupOn": true, "isChatOn": false, "isCommunityOn": true, "isBurrowOn": true, "isNoticeOn": true }
				""");

		assertThat(changed.statusCode()).isEqualTo(200);
		// 안 읽은 채팅 알림은 그대로 세지만, 채팅 알림을 껐으므로 빨간 점은 꺼진다.
		assertThat(receivers.await(UNREAD_CHANGED).data()).isEqualTo("{\"unreadCount\":1,\"hasUnread\":false}");
	}

	private EventStreamClient open(String cookie) throws Exception {
		EventStreamClient stream = server.eventStream(cookie);
		streams.add(stream);
		assertThat(stream.status()).isEqualTo(200);
		stream.awaitPings(1);
		return stream;
	}

	private void sendMessage(String content) throws Exception {
		HttpResponse<String> response = server.post("/api/v1/chat-rooms/" + room + "/messages", senderCookie, """
				{ "content": "%s" }
				""".formatted(content));
		assertThat(response.statusCode()).isEqualTo(201);
	}

}
