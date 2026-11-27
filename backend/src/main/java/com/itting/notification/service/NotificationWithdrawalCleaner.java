package com.itting.notification.service;

import com.itting.notification.repository.NotificationRepository;
import com.itting.notification.repository.NotificationSettingRepository;
import com.itting.notification.repository.PushSubscriptionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴의 알림 영역 정리({@code docs/api/10-settings.md}의 "탈퇴할 때 하는 일"). 탈퇴 트랜잭션 안에서만 부르고, 다른 영역의
 * 정리가 알림을 만든 뒤에 부른다. 알림 설정, 브라우저 푸시 등록, 내가 받은 알림을 지운다. 내가 일으킨 남의 알림은 남긴다.
 */
@Component
public class NotificationWithdrawalCleaner {

	private final NotificationRepository notifications;

	private final NotificationSettingRepository settings;

	private final PushSubscriptionRepository subscriptions;

	public NotificationWithdrawalCleaner(NotificationRepository notifications, NotificationSettingRepository settings,
			PushSubscriptionRepository subscriptions) {
		this.notifications = notifications;
		this.settings = settings;
		this.subscriptions = subscriptions;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void clean(Long userId) {
		notifications.deleteAllByUserId(userId);
		settings.deleteByUserIdNow(userId);
		subscriptions.deleteAllByUserId(userId);
	}

}
