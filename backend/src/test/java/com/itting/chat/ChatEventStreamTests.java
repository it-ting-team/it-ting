package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.itting.chat.service.MeetupChatRoomService;
import com.itting.global.config.FrontendProperties;
import com.itting.realtime.service.EventStreamBus;
import com.itting.realtime.service.EventStreamConnections;
import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.EventStreamClient;
import com.itting.support.EventStreamClient.Event;
import com.itting.support.EventStreamMarker;
import com.itting.support.RealServer;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 채팅의 실시간 소식 {@code CHAT_MESSAGE_CREATED}, {@code CHAT_REQUEST_COUNT_CHANGED}가 열어 둔 연결
 * ({@code GET /api/v1/me/event-stream})로 오는지 본다. 서버를 실제 포트로 띄우고, 일은 API를 HTTP로 불러 일으킨다.
 * 서버가 다른 스레드에서 돌아 테스트 트랜잭션을 쓸 수 없으므로 넣은 줄은 끝나고 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ChatEventStreamTests {

	private static final String PASSWORD = CommunitySteps.PASSWORD;

	private static final String MESSAGE_CREATED = "CHAT_MESSAGE_CREATED";

	private static final String REQUEST_COUNT_CHANGED = "CHAT_REQUEST_COUNT_CHANGED";

	@LocalServerPort
	int port;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	EventStreamMarker marker;

	@Autowired
	EventStreamBus bus;

	@Autowired
	EventStreamConnections connections;

	@Autowired
	MeetupChatRoomService meetupChatRoomService;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	FrontendProperties frontend;

	@Autowired
	JdbcTemplate jdbcTemplate;

	RealServer server;

	long me;

	long other;

	long stranger;

	String myCookie;

	String otherCookie;

	String strangerCookie;

	private final List<EventStreamClient> streams = new ArrayList<>();

	private final List<Long> meetupIds = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		server = new RealServer(port);
		me = steps.createMember("chat-stream-me@example.com", "소식나");
		other = steps.createMember("chat-stream-other@example.com", "소식너");
		stranger = steps.createMember("chat-stream-stranger@example.com", "소식남");
		myCookie = server.login("chat-stream-me@example.com", PASSWORD);
		otherCookie = server.login("chat-stream-other@example.com", PASSWORD);
		strangerCookie = server.login("chat-stream-stranger@example.com", PASSWORD);
	}

	@AfterEach
	void cleanUp() {
		EventStreamClient.closeAll(streams, connections);
		chat.deleteChatOf(me, other, stranger);
		for (Long meetupId : meetupIds) {
			jdbcTemplate.update("DELETE FROM meetup_application WHERE meetup_id = ?", meetupId);
			jdbcTemplate.update("DELETE FROM meetup WHERE id = ?", meetupId);
		}
		chat.deleteAllOf(me, other, stranger);
	}

	@Test
	void 메시지를_보내면_대화방의_두_사람이_받고_안_읽은_수는_받는_사람마다_센다() throws Exception {
		long room = chat.insertDirectRoom(me, other, Instant.now());
		EventStreamClient mine = open(myCookie);
		EventStreamClient others = open(otherCookie);

		String sent = sendText(myCookie, room, "토요일 아침에 한강 어때요?");

		Event toOther = others.await(MESSAGE_CREATED);
		assertThat(JsonPath.<Number>read(toOther.data(), "$.chatRoomId").longValue()).isEqualTo(room);
		assertThat(JsonPath.<Integer>read(toOther.data(), "$.unreadCount")).isEqualTo(1);
		// 메시지는 보내기의 응답과 같은 모양, 같은 값이다.
		assertThat(JsonPath.<Map<String, Object>>read(toOther.data(), "$.message"))
				.isEqualTo(JsonPath.<Map<String, Object>>read(sent, "$.data"));
		assertThat(JsonPath.<String>read(toOther.data(), "$.message.content")).isEqualTo("토요일 아침에 한강 어때요?");
		assertThat(JsonPath.<String>read(toOther.data(), "$.message.sender.nickname")).isEqualTo("소식나");

		// 보낸 사람도 받는다. 내가 보낸 메시지는 안 읽은 수에 세지 않는다.
		Event toMe = mine.await(MESSAGE_CREATED);
		assertThat(JsonPath.<Integer>read(toMe.data(), "$.unreadCount")).isZero();
		assertThat(JsonPath.<Map<String, Object>>read(toMe.data(), "$.message"))
				.isEqualTo(JsonPath.<Map<String, Object>>read(sent, "$.data"));

		sendText(myCookie, room, "7시에 여의나루역에서 봐요.");

		assertThat(JsonPath.<Integer>read(others.await(MESSAGE_CREATED).data(), "$.unreadCount")).isEqualTo(2);
		assertThat(JsonPath.<Integer>read(mine.await(MESSAGE_CREATED).data(), "$.unreadCount")).isZero();
	}

	@Test
	void 메시지의_사진과_모임_카드도_담긴다() throws Exception {
		long room = chat.insertDirectRoom(me, other, Instant.now());
		long meetup = meetup(other, "토요일 한강 러닝");
		long image = steps.uploadImage(me, "https://cdn.example.com/images/stream.webp");
		EventStreamClient others = open(otherCookie);

		HttpResponse<String> sent = server.post("/api/v1/chat-rooms/" + room + "/messages", myCookie, """
				{ "content": "같이 가요 %s/meetups/%d", "imageIds": [%d] }
				""".formatted(frontend.frontendUrl(), meetup, image));

		assertThat(sent.statusCode()).isEqualTo(201);
		Event event = others.await(MESSAGE_CREATED);
		assertThat(JsonPath.<Map<String, Object>>read(event.data(), "$.message"))
				.isEqualTo(JsonPath.<Map<String, Object>>read(sent.body(), "$.data"));
		assertThat(JsonPath.<Number>read(event.data(), "$.message.meetupCard.meetupId").longValue()).isEqualTo(meetup);
		assertThat(JsonPath.<String>read(event.data(), "$.message.meetupCard.title")).isEqualTo("토요일 한강 러닝");
		assertThat(JsonPath.<String>read(event.data(), "$.message.images[0].url"))
				.isEqualTo("https://cdn.example.com/images/stream.webp");
	}

	@Test
	void 한_계정이_연_연결은_모두_받는다() throws Exception {
		long room = chat.insertDirectRoom(me, other, Instant.now());
		EventStreamClient tab = open(otherCookie);
		EventStreamClient phone = open(server.login("chat-stream-other@example.com", PASSWORD));

		sendText(myCookie, room, "안녕하세요");

		assertThat(JsonPath.<String>read(tab.await(MESSAGE_CREATED).data(), "$.message.content")).isEqualTo("안녕하세요");
		assertThat(JsonPath.<String>read(phone.await(MESSAGE_CREATED).data(), "$.message.content"))
				.isEqualTo("안녕하세요");
	}

	@Test
	void 대화방에_없는_사람은_받지_않는다() throws Exception {
		long room = chat.insertDirectRoom(me, other, Instant.now());
		EventStreamClient others = open(otherCookie);
		EventStreamClient strangers = open(strangerCookie);

		sendText(myCookie, room, "둘만의 이야기");

		others.await(MESSAGE_CREATED);
		assertNoMore(strangers, stranger, MESSAGE_CREATED);
	}

	@Test
	void 나간_사람은_받지_않고_남은_사람은_나갔다는_안내_메시지를_받는다() throws Exception {
		long room = chat.insertDirectRoom(me, other, Instant.now());
		EventStreamClient mine = open(myCookie);
		EventStreamClient others = open(otherCookie);

		assertThat(server.post("/api/v1/chat-rooms/" + room + "/leave", myCookie, null).statusCode()).isEqualTo(200);

		Event left = others.await(MESSAGE_CREATED);
		assertThat(JsonPath.<Number>read(left.data(), "$.chatRoomId").longValue()).isEqualTo(room);
		assertThat(JsonPath.<String>read(left.data(), "$.message.type")).isEqualTo("LEFT");
		assertThat(JsonPath.<Number>read(left.data(), "$.message.sender.id").longValue()).isEqualTo(me);
		assertThat(JsonPath.<Object>read(left.data(), "$.message.content")).isNull();
		// 안내 메시지는 안 읽은 수에 세지 않는다.
		assertThat(JsonPath.<Integer>read(left.data(), "$.unreadCount")).isZero();
		assertNoMore(mine, me, MESSAGE_CREATED);
	}

	@Test
	void 모임_대화방에_들어오면_있던_사람과_들어온_사람이_안내_메시지를_받는다() throws Exception {
		long meetup = meetup(me, "보드게임 모임");
		long room = chat.insertMeetupRoom(meetup, me, Instant.now());
		EventStreamClient mine = open(myCookie);
		EventStreamClient others = open(otherCookie);
		EventStreamClient strangers = open(strangerCookie);

		// 모임의 참여 수락이 부르는 것
		transactionTemplate.executeWithoutResult(status -> meetupChatRoomService.join(meetup, other, Instant.now()));

		for (EventStreamClient stream : List.of(mine, others)) {
			Event joined = stream.await(MESSAGE_CREATED);
			assertThat(JsonPath.<Number>read(joined.data(), "$.chatRoomId").longValue()).isEqualTo(room);
			assertThat(JsonPath.<String>read(joined.data(), "$.message.type")).isEqualTo("JOINED");
			assertThat(JsonPath.<Number>read(joined.data(), "$.message.sender.id").longValue()).isEqualTo(other);
		}

		sendText(otherCookie, room, "반갑습니다");

		assertThat(JsonPath.<Integer>read(mine.await(MESSAGE_CREATED).data(), "$.unreadCount")).isEqualTo(1);
		assertThat(JsonPath.<Integer>read(others.await(MESSAGE_CREATED).data(), "$.unreadCount")).isZero();
		assertNoMore(strangers, stranger, MESSAGE_CREATED);
	}

	@Test
	void 삭제된_모임의_대화방은_아무도_받지_않는다() throws Exception {
		long meetup = meetup(me, "지워질 모임");
		long room = chat.insertMeetupRoom(meetup, me, Instant.now());
		chat.join(room, other, Instant.now());
		long message = chat.text(room, me, "곧 지워집니다", Instant.now());
		steps.deleteMeetup(meetup);
		EventStreamClient mine = open(myCookie);
		EventStreamClient others = open(otherCookie);

		bus.publish(MESSAGE_CREATED, Map.of("chatRoomId", room, "messageId", message));

		assertNoMore(mine, me, MESSAGE_CREATED);
		assertNoMore(others, other, MESSAGE_CREATED);
	}

	@Test
	void 요청을_받거나_보낸_사람이_취소하면_받은_사람에게_요청_수가_온다() throws Exception {
		EventStreamClient mine = open(myCookie);
		EventStreamClient others = open(otherCookie);

		HttpResponse<String> sent = server.post("/api/v1/chat-requests", myCookie, """
				{ "receiverId": %d, "message": "저도 러닝 좋아해요" }
				""".formatted(other));

		assertThat(sent.statusCode()).isEqualTo(201);
		assertThat(others.await(REQUEST_COUNT_CHANGED).data()).isEqualTo("{\"receivedCount\":1}");
		// 보낸 사람에게는 오지 않는다.
		assertNoMore(mine, me, REQUEST_COUNT_CHANGED);

		long requestId = JsonPath.<Number>read(sent.body(), "$.data.chatRequestId").longValue();
		assertThat(server.post("/api/v1/chat-requests/" + requestId + "/cancel", myCookie, null).statusCode())
				.isEqualTo(200);

		assertThat(others.await(REQUEST_COUNT_CHANGED).data()).isEqualTo("{\"receivedCount\":0}");
		assertNoMore(mine, me, REQUEST_COUNT_CHANGED);
	}

	@Test
	void 요청을_수락하면_나에게_요청_수가_오고_두_사람에게_수락_안내_메시지가_온다() throws Exception {
		long request = chat.insertRequest(other, me, "안녕하세요", "PENDING", Instant.now().minus(Duration.ofMinutes(5)),
				null);
		EventStreamClient mine = open(myCookie);
		EventStreamClient others = open(otherCookie);

		HttpResponse<String> accepted = server.post("/api/v1/chat-requests/" + request + "/accept", myCookie, null);

		assertThat(accepted.statusCode()).isEqualTo(200);
		long room = JsonPath.<Number>read(accepted.body(), "$.data.chatRoomId").longValue();
		assertThat(mine.await(REQUEST_COUNT_CHANGED).data()).isEqualTo("{\"receivedCount\":0}");
		for (EventStreamClient stream : List.of(mine, others)) {
			Event event = stream.await(MESSAGE_CREATED);
			assertThat(JsonPath.<Number>read(event.data(), "$.chatRoomId").longValue()).isEqualTo(room);
			assertThat(JsonPath.<String>read(event.data(), "$.message.type")).isEqualTo("ACCEPTED");
			assertThat(JsonPath.<Number>read(event.data(), "$.message.sender.id").longValue()).isEqualTo(me);
		}
		assertNoMore(others, other, REQUEST_COUNT_CHANGED);
	}

	/** 연결을 열고 첫 신호까지 기다린다. 그 뒤에 생긴 일의 소식은 이 연결로 온다. */
	private EventStreamClient open(String cookie) throws Exception {
		EventStreamClient stream = server.eventStream(cookie);
		streams.add(stream);
		assertThat(stream.status()).isEqualTo(200);
		stream.awaitPings(1);
		return stream;
	}

	/** @return 보내기의 응답 본문 */
	private String sendText(String cookie, long room, String content) throws Exception {
		HttpResponse<String> response = server.post("/api/v1/chat-rooms/" + room + "/messages", cookie, """
				{ "content": "%s" }
				""".formatted(content));
		assertThat(response.statusCode()).isEqualTo(201);
		return response.body();
	}

	private long meetup(long hostId, String title) {
		long meetupId = steps.insertMeetup(hostId, title, Instant.now().plus(Duration.ofDays(3)), 6, null);
		meetupIds.add(meetupId);
		return meetupId;
	}

	/** 지금까지 일어난 일의 소식이 모두 도착한 뒤에도, 이 연결에 그 이름의 소식이 없다. */
	private void assertNoMore(EventStreamClient stream, long userId, String name) throws Exception {
		marker.mark(userId);
		stream.await(EventStreamMarker.NAME);
		assertThat(stream.pending()).noneMatch(event -> event.name().equals(name));
	}

}
