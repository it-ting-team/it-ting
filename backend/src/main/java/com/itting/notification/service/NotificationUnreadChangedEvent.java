package com.itting.notification.service;

/**
 * 이 사람의 알림이 생겼거나 읽음이 바뀌었다. 바꾼 트랜잭션이 커밋된 뒤에 낸다.
 * 실시간 소식(06 채팅의 이벤트 연결)이 받아 {@code NOTIFICATION_UNREAD_CHANGED}로 보낸다. 값은
 * {@link NotificationQueryService#unreadCount(Long)}로 계산한다.
 *
 * @param userId 알림을 받는 사람
 */
public record NotificationUnreadChangedEvent(Long userId) {
}
