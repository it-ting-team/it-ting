package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.itting.chat.dto.ChatRequestAcceptResponse;
import com.itting.chat.dto.ChatRequestCreateRequest;
import com.itting.chat.dto.ChatRequestCreateResponse;
import com.itting.chat.service.ChatMessageCreatedEvent;
import com.itting.chat.service.ChatRequestCountChangedEvent;
import com.itting.chat.service.ChatRequestService;
import com.itting.global.error.BusinessException;
import com.itting.support.ChatSteps;
import com.itting.support.CommunitySteps;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대화 요청이 바뀌면 커밋된 뒤에만 이벤트를 낸다. 받은 요청 수가 바뀌면 {@link ChatRequestCountChangedEvent}, 대화방에 메시지가
 * 생기면 {@link ChatMessageCreatedEvent}다(실시간 소식이 이 이벤트를 받는다). 되돌아가거나 실패하면 이벤트도 없다. 커밋해야
 * 하므로 테스트 트랜잭션을 쓰지 않고, 만든 줄은 끝에 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ChatRequestEventTests {

	@Autowired
	ChatRequestService chatRequestService;

	@Autowired
	CommunitySteps steps;

	@Autowired
	ChatSteps chat;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	ApplicationEvents events;

	long me;

	long other;

	@BeforeEach
	void setUp() {
		me = steps.createMember("event-me@example.com", "이벤트나");
		other = steps.createMember("event-other@example.com", "이벤트너");
	}

	@AfterEach
	void cleanUp() {
		chat.deleteAllOf(me, other);
	}

	@Test
	void 요청을_보내면_받은_사람의_요청_수가_바뀐_것을_한_번_낸다() {
		ChatRequestCreateResponse response = chatRequestService.send(me, new ChatRequestCreateRequest(other, "안녕"));

		assertThat(response.chatRequestId()).isNotNull();
		assertThat(countChanged()).containsExactly(new ChatRequestCountChangedEvent(other));
		assertThat(messageCreated()).isEmpty();
	}

	@Test
	void 보내지_못하면_이벤트도_없다() {
		chatRequestService.send(me, new ChatRequestCreateRequest(other, null));
		events.clear();

		assertThatThrownBy(() -> chatRequestService.send(me, new ChatRequestCreateRequest(other, null)))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> chatRequestService.send(me, new ChatRequestCreateRequest(me, null)))
				.isInstanceOf(BusinessException.class);

		assertThat(countChanged()).isEmpty();
		assertThat(messageCreated()).isEmpty();
	}

	@Test
	void 수락하면_수락한_사람의_요청_수와_안내_메시지가_생긴_것을_낸다() {
		long request = chat.insertRequest(other, me, "인사", "PENDING", Instant.now().minus(Duration.ofHours(1)), null);

		ChatRequestAcceptResponse response = chatRequestService.accept(me, request);

		List<Long> messageIds = chat.messages(response.chatRoomId()).stream()
				.map(message -> ((Number) message.get("id")).longValue())
				.toList();
		assertThat(countChanged()).containsExactly(new ChatRequestCountChangedEvent(me));
		assertThat(messageCreated()).containsExactly(new ChatMessageCreatedEvent(response.chatRoomId(), messageIds.getFirst()));
		assertThat(messageIds).hasSize(1);
	}

	@Test
	void 이미_수락한_요청을_다시_수락하면_이벤트가_없다() {
		long request = chat.insertRequest(other, me, null, "PENDING", Instant.now().minus(Duration.ofHours(1)), null);
		chatRequestService.accept(me, request);
		events.clear();

		chatRequestService.accept(me, request);

		assertThat(countChanged()).isEmpty();
		assertThat(messageCreated()).isEmpty();
	}

	@Test
	void 수락하지_못하면_이벤트도_알림도_없다() {
		long canceled = chat.insertRequest(other, me, null, "CANCELED", Instant.now().minus(Duration.ofHours(1)), null);

		assertThatThrownBy(() -> chatRequestService.accept(me, canceled)).isInstanceOf(BusinessException.class);

		assertThat(countChanged()).isEmpty();
		assertThat(messageCreated()).isEmpty();
		assertThat(chat.notifications(other)).isEmpty();
		assertThat(chat.roomCount()).isZero();
	}

	@Test
	void 거절하면_받은_사람의_요청_수가_바뀐_것을_내고_다시_거절하면_내지_않는다() {
		long request = chat.insertRequest(other, me, null, "PENDING", Instant.now().minus(Duration.ofHours(1)), null);

		chatRequestService.reject(me, request);

		assertThat(countChanged()).containsExactly(new ChatRequestCountChangedEvent(me));
		events.clear();

		chatRequestService.reject(me, request);

		assertThat(countChanged()).isEmpty();
	}

	@Test
	void PENDING인_요청을_취소하면_받은_사람의_요청_수가_바뀐_것을_낸다() {
		long request = chat.insertRequest(me, other, null, "PENDING", Instant.now().minus(Duration.ofHours(1)), null);

		chatRequestService.cancel(me, request);

		assertThat(countChanged()).containsExactly(new ChatRequestCountChangedEvent(other));
	}

	@Test
	void 받은_사람이_거절한_요청을_취소하면_받은_요청_수가_그대로라서_이벤트가_없다() {
		long request = chat.insertRequest(me, other, null, "REJECTED", Instant.now().minus(Duration.ofHours(1)), null);

		chatRequestService.cancel(me, request);

		assertThat(chat.requestStatus(request)).isEqualTo("CANCELED");
		assertThat(countChanged()).isEmpty();
	}

	@Test
	void 내가_거절한_사람에게_보내면_메시지가_생긴_것을_인사_포함_두_번_내고_요청_수_이벤트는_없다() {
		long request = chat.insertRequest(other, me, null, "REJECTED", Instant.now().minus(Duration.ofHours(1)), null);

		ChatRequestCreateResponse response = chatRequestService.send(me, new ChatRequestCreateRequest(other, "생각이 바뀌었어요"));

		long roomId = response.chatRoomId();
		List<Long> messageIds = chat.messages(roomId).stream()
				.map(message -> ((Number) message.get("id")).longValue())
				.toList();
		assertThat(messageIds).hasSize(2);
		assertThat(messageCreated()).containsExactly(new ChatMessageCreatedEvent(roomId, messageIds.get(0)),
				new ChatMessageCreatedEvent(roomId, messageIds.get(1)));
		// 거절된 요청은 받은 요청 수에 들지 않았고 새 요청도 만들지 않았으므로 두 사람의 받은 요청 수는 그대로다.
		assertThat(countChanged()).isEmpty();
		assertThat(chat.requestStatus(request)).isEqualTo("ACCEPTED");
	}

	@Test
	void 바깥_트랜잭션이_커밋하기_전에는_이벤트를_내지_않고_되돌리면_이벤트도_변경도_없다() {
		long request = chat.insertRequest(other, me, null, "PENDING", Instant.now().minus(Duration.ofHours(1)), null);

		transactionTemplate.executeWithoutResult(status -> {
			chatRequestService.accept(me, request);
			// 같은 트랜잭션 안에서는 아직 나가지 않는다.
			assertThat(countChanged()).isEmpty();
			assertThat(messageCreated()).isEmpty();
			status.setRollbackOnly();
		});

		assertThat(countChanged()).isEmpty();
		assertThat(messageCreated()).isEmpty();
		assertThat(chat.requestStatus(request)).isEqualTo("PENDING");
		assertThat(chat.roomCount()).isZero();
		assertThat(chat.notifications(other)).isEmpty();
	}

	@Test
	void 바깥_트랜잭션이_커밋하면_그때_이벤트를_낸다() {
		long request = chat.insertRequest(other, me, null, "PENDING", Instant.now().minus(Duration.ofHours(1)), null);

		transactionTemplate.executeWithoutResult(status -> {
			chatRequestService.reject(me, request);
			assertThat(countChanged()).isEmpty();
		});

		assertThat(countChanged()).containsExactly(new ChatRequestCountChangedEvent(me));
		assertThat(chat.requestStatus(request)).isEqualTo("REJECTED");
	}

	private List<ChatRequestCountChangedEvent> countChanged() {
		return events.stream(ChatRequestCountChangedEvent.class).toList();
	}

	private List<ChatMessageCreatedEvent> messageCreated() {
		return events.stream(ChatMessageCreatedEvent.class).toList();
	}

}
