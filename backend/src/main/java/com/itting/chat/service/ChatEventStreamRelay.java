package com.itting.chat.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itting.chat.domain.ChatMessage;
import com.itting.chat.dto.ChatMessageCreatedEventResponse;
import com.itting.chat.dto.ChatMessageResponse;
import com.itting.chat.dto.ChatRequestCountChangedEventResponse;
import com.itting.chat.repository.ChatMessageRepository;
import com.itting.chat.repository.ChatRoomMemberRepository;
import com.itting.chat.repository.ChatRoomReadRepository;
import com.itting.realtime.service.EventStreamBus;
import com.itting.realtime.service.EventStreamConnections;
import com.itting.realtime.service.EventStreamRelay;
import com.itting.realtime.service.EventStreamSignal;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 채팅의 실시간 소식 둘을 브라우저로 보낸다({@code docs/api/06-chat.md}의 {@code GET /api/v1/me/event-stream}).
 *
 * <ul>
 * <li>{@code CHAT_MESSAGE_CREATED}: 대화방에 메시지가 생겼다. 그 대화방에 지금 들어갈 수 있는 모든 사람이 받는다(보낸 사람도).
 * 나간 사람과 삭제된 모임의 대화방에 있던 사람은 받지 않는다. 값의 안 읽은 수는 받는 사람마다 센다.
 * <li>{@code CHAT_REQUEST_COUNT_CHANGED}: 받은 요청 수가 바뀌었다. 요청을 받은 사람이 받는다.
 * </ul>
 *
 * <p>채팅 서비스가 커밋 뒤에 내는 이벤트를 받아 신호를 싣고, 신호가 돌아오면 이 서버에 붙어 있는 사람의 값만 만든다. 값은 조회
 * API와 같은 코드로 계산한다. 붙어 있는 사람이 없으면 DB를 읽지 않는다.
 */
@Component
class ChatEventStreamRelay implements EventStreamRelay {

	static final String MESSAGE_CREATED = "CHAT_MESSAGE_CREATED";

	static final String REQUEST_COUNT_CHANGED = "CHAT_REQUEST_COUNT_CHANGED";

	private static final String CHAT_ROOM_ID = "chatRoomId";

	private static final String MESSAGE_ID = "messageId";

	private static final String USER_ID = "userId";

	private final EventStreamBus bus;

	private final EventStreamConnections connections;

	private final ChatRoomMemberRepository memberRepository;

	private final ChatMessageRepository messageRepository;

	private final ChatRoomReadRepository readRepository;

	private final ChatMessageAssembler assembler;

	private final ChatRequestQueryService requestQueryService;

	private final TransactionTemplate readOnly;

	ChatEventStreamRelay(EventStreamBus bus, EventStreamConnections connections,
			ChatRoomMemberRepository memberRepository, ChatMessageRepository messageRepository,
			ChatRoomReadRepository readRepository, ChatMessageAssembler assembler,
			ChatRequestQueryService requestQueryService, PlatformTransactionManager transactionManager) {
		this.bus = bus;
		this.connections = connections;
		this.memberRepository = memberRepository;
		this.messageRepository = messageRepository;
		this.readRepository = readRepository;
		this.assembler = assembler;
		this.requestQueryService = requestQueryService;
		this.readOnly = new TransactionTemplate(transactionManager);
		this.readOnly.setReadOnly(true);
	}

	@EventListener
	void onMessageCreated(ChatMessageCreatedEvent event) {
		bus.publish(MESSAGE_CREATED, Map.of(CHAT_ROOM_ID, event.chatRoomId(), MESSAGE_ID, event.messageId()));
	}

	@EventListener
	void onRequestCountChanged(ChatRequestCountChangedEvent event) {
		bus.publish(REQUEST_COUNT_CHANGED, Map.of(USER_ID, event.userId()));
	}

	@Override
	public Set<String> signalTypes() {
		return Set.of(MESSAGE_CREATED, REQUEST_COUNT_CHANGED);
	}

	@Override
	public void deliver(EventStreamSignal signal) {
		if (connections.isEmpty()) {
			return;
		}
		if (MESSAGE_CREATED.equals(signal.type())) {
			Long chatRoomId = signal.arg(CHAT_ROOM_ID);
			Long messageId = signal.arg(MESSAGE_ID);
			readOnly.executeWithoutResult(status -> deliverMessage(chatRoomId, messageId));
		}
		else {
			deliverRequestCount(signal.arg(USER_ID));
		}
	}

	private void deliverMessage(Long chatRoomId, Long messageId) {
		Set<Long> receivers = connections.connectedAmong(memberRepository.findJoinedUserIds(chatRoomId));
		if (receivers.isEmpty()) {
			return;
		}
		ChatMessage message = messageRepository.findById(messageId)
				.filter(found -> found.getChatRoomId().equals(chatRoomId))
				.orElse(null);
		if (message == null) {
			return;
		}
		ChatMessageResponse response = assembler.assemble(message);
		for (Long receiverId : receivers) {
			// 들어갈 수 있는 대화방만 세어 준다. 그 사이에 나갔거나 모임이 삭제됐으면 값이 없고, 그 사람에게는 보내지 않는다.
			Long unreadCount = readRepository.countUnreadByRoomIds(receiverId, List.of(chatRoomId)).get(chatRoomId);
			if (unreadCount != null) {
				connections.send(receiverId, MESSAGE_CREATED,
						new ChatMessageCreatedEventResponse(chatRoomId, response, unreadCount));
			}
		}
	}

	private void deliverRequestCount(Long userId) {
		if (!connections.isConnected(userId)) {
			return;
		}
		connections.send(userId, REQUEST_COUNT_CHANGED,
				new ChatRequestCountChangedEventResponse(requestQueryService.countReceivedPending(userId)));
	}

}
