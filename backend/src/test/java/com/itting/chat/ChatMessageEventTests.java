package com.itting.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.itting.chat.dto.ChatMessageResponse;
import com.itting.chat.dto.ChatMessageSendRequest;
import com.itting.chat.service.ChatMessageCreatedEvent;
import com.itting.chat.service.ChatMessageService;
import com.itting.chat.service.ChatRoomService;
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
 * 대화방에 메시지가 생기면 커밋된 뒤에만 {@link ChatMessageCreatedEvent}를 낸다(실시간 소식이 이 이벤트를 받는다). 사람이 보낸
 * 메시지와 나갔다는 안내 메시지 모두다. 실패하거나 되돌아가면 이벤트도 없다. 커밋해야 이벤트가 나오므로 테스트 트랜잭션을 쓰지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ChatMessageEventTests {

	@Autowired
	ChatMessageService chatMessageService;

	@Autowired
	ChatRoomService chatRoomService;

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

	long room;

	@BeforeEach
	void setUp() {
		me = steps.createMember("message-event-me@example.com", "메시지나");
		other = steps.createMember("message-event-other@example.com", "메시지너");
		room = chat.insertDirectRoom(me, other, Instant.now());
	}

	@AfterEach
	void cleanUp() {
		chat.deleteAllOf(me, other);
	}

	@Test
	void 메시지를_보내면_그_메시지가_생긴_것을_한_번_낸다() {
		ChatMessageResponse message = chatMessageService.send(me, room, text("안녕하세요"));

		assertThat(messageCreated()).containsExactly(new ChatMessageCreatedEvent(room, message.id()));
	}

	@Test
	void 보내지_못하면_이벤트도_없다() {
		chat.leave(room, other);

		assertThatThrownBy(() -> chatMessageService.send(me, room, text("아직 계세요?")))
				.isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> chatMessageService.send(me, room, new ChatMessageSendRequest(" ", List.of())))
				.isInstanceOf(BusinessException.class);

		assertThat(messageCreated()).isEmpty();
		assertThat(chat.messages(room)).isEmpty();
	}

	@Test
	void 나가면_나갔다는_안내_메시지가_생긴_것을_낸다() {
		chatRoomService.leave(me, room);

		Map<String, Object> left = chat.messages(room).getLast();
		assertThat(left).containsEntry("type", "LEFT");
		assertThat(messageCreated())
				.containsExactly(new ChatMessageCreatedEvent(room, ((Number) left.get("id")).longValue()));
	}

	@Test
	void 바깥_트랜잭션이_되돌아가면_이벤트가_나오지_않는다() {
		transactionTemplate.executeWithoutResult(status -> {
			chatMessageService.send(me, room, text("되돌아갈 메시지"));
			status.setRollbackOnly();
		});

		assertThat(messageCreated()).isEmpty();
		assertThat(chat.messages(room)).isEmpty();
	}

	private List<ChatMessageCreatedEvent> messageCreated() {
		return events.stream(ChatMessageCreatedEvent.class).toList();
	}

	private static ChatMessageSendRequest text(String content) {
		return new ChatMessageSendRequest(content, List.of());
	}

}
