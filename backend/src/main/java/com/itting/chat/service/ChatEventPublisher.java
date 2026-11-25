package com.itting.chat.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 채팅의 이벤트를 트랜잭션이 커밋된 뒤에 낸다. 되돌려진 일을 알리지 않는다. 브라우저로 보내는 일(실시간 소식)은 이 이벤트를 받는 쪽이
 * 한다.
 */
@Component
class ChatEventPublisher {

	private final ApplicationEventPublisher publisher;

	ChatEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	/** 이 사람이 받은 요청 수가 바뀌었다. */
	void requestCountChanged(Long userId) {
		afterCommit(new ChatRequestCountChangedEvent(userId));
	}

	/** 대화방에 메시지가 생겼다. */
	void messageCreated(Long chatRoomId, Long messageId) {
		afterCommit(new ChatMessageCreatedEvent(chatRoomId, messageId));
	}

	private void afterCommit(Object event) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			publisher.publishEvent(event);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				publisher.publishEvent(event);
			}
		});
	}

}
