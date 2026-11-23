package com.itting.notification.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 알림 이벤트를 트랜잭션이 커밋된 뒤에 낸다. 되돌려진 알림을 알리지 않는다.
 */
@Component
class NotificationEventPublisher {

	private final ApplicationEventPublisher publisher;

	NotificationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	/** 알림이 생겼다. 생긴 것과 안 읽은 수가 바뀐 것을 함께 낸다. */
	void created(Long notificationId, Long userId) {
		afterCommit(new NotificationCreatedEvent(notificationId, userId));
		unreadChanged(userId);
	}

	void unreadChanged(Long userId) {
		afterCommit(new NotificationUnreadChangedEvent(userId));
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
