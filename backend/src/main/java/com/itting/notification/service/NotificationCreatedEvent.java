package com.itting.notification.service;

/**
 * 알림이 생겼다(새 메시지 알림은 시각이 바뀌었을 때도). 알림을 만든 트랜잭션이 커밋된 뒤에 낸다.
 * 브라우저 푸시(10 설정)가 받는다.
 *
 * @param notificationId 생긴 알림의 id
 * @param userId 받는 사람
 */
public record NotificationCreatedEvent(Long notificationId, Long userId) {
}
