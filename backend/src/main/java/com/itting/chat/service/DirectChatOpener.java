package com.itting.chat.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.itting.chat.domain.ChatMessage;
import com.itting.chat.domain.ChatRequest;
import com.itting.chat.domain.ChatRoom;
import com.itting.chat.domain.ChatRoomMember;
import com.itting.chat.repository.ChatMessageRepository;
import com.itting.chat.repository.ChatRoomMemberRepository;
import com.itting.chat.repository.ChatRoomRepository;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 받은 사람이 대화 요청을 수락할 때 1:1 대화방을 여는 일을 한 곳에서 한다. 대화 요청을 수락하는 API와, 내가 거절한 요청이 남아 있는
 * 사람에게 요청을 보내는 API(그 요청을 내가 수락한 것으로 처리한다)가 같은 일을 하므로 둘이 함께 쓴다.
 *
 * <p>부른 쪽의 트랜잭션 안에서만 부를 수 있다. 두 사람의 {@code user} 줄을 잠근 뒤여야 하고, 방 · 참여자 · 메시지 · 요청의 상태가
 * 한 번에 커밋되어야 한다.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class DirectChatOpener {

	private final ChatRoomRepository chatRoomRepository;

	private final ChatRoomMemberRepository chatRoomMemberRepository;

	private final ChatMessageRepository chatMessageRepository;

	private final NotificationService notificationService;

	private final ChatEventPublisher events;

	private final Clock clock;

	DirectChatOpener(ChatRoomRepository chatRoomRepository, ChatRoomMemberRepository chatRoomMemberRepository,
			ChatMessageRepository chatMessageRepository, NotificationService notificationService,
			ChatEventPublisher events, Clock clock) {
		this.chatRoomRepository = chatRoomRepository;
		this.chatRoomMemberRepository = chatRoomMemberRepository;
		this.chatMessageRepository = chatMessageRepository;
		this.notificationService = notificationService;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 받은 사람이 요청을 수락한 것으로 1:1 대화방을 열고, 요청을 {@code ACCEPTED}로 바꾼다.
	 *
	 * <ol>
	 * <li>대화방과 두 사람의 참여자 줄을 만든다.
	 * <li>수락 안내 메시지({@code ACCEPTED}, 보낸 사람은 수락한 사람)를 넣는다. 요청에 붙은 인사는 옮기지 않는다.
	 * <li>{@code greeting}이 있으면 수락한 사람이 보낸 첫 메시지({@code TEXT})로 넣고, 그 사람의 읽은 자리를 그 메시지로 둔다.
	 * <li>요청을 보낸 사람에게 수락 알림을 만들고, 인사를 넣었으면 새 메시지 알림도 만든다.
	 * <li>메시지마다 커밋 뒤에 {@link ChatMessageCreatedEvent}를 낸다.
	 * </ol>
	 *
	 * @param greeting 수락한 사람이 대화방에 보낼 첫 메시지. 없으면 null
	 * @return 만든 대화방의 id
	 */
	Long open(ChatRequest request, String greeting) {
		Long requesterId = request.getSenderId();
		Long accepterId = request.getReceiverId();
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

		Long roomId = chatRoomRepository.save(ChatRoom.createDirect()).getId();
		Long acceptedMessageId = chatMessageRepository.save(ChatMessage.accepted(roomId, accepterId)).getId();
		Long greetingMessageId = greeting == null ? null
				: chatMessageRepository.save(ChatMessage.text(roomId, accepterId, greeting)).getId();
		chatRoomMemberRepository.save(ChatRoomMember.join(roomId, requesterId, now, null));
		chatRoomMemberRepository.save(ChatRoomMember.join(roomId, accepterId, now, greetingMessageId));
		request.accept(roomId, now);

		notificationService.notify(requesterId, NotificationType.CHAT_REQUEST_ACCEPTED, accepterId, roomId);
		events.messageCreated(roomId, acceptedMessageId);
		if (greetingMessageId != null) {
			notificationService.notifyChatMessage(requesterId, roomId, accepterId);
			events.messageCreated(roomId, greetingMessageId);
		}
		return roomId;
	}

}
