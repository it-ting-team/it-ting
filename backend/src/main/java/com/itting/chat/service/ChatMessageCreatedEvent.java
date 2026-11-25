package com.itting.chat.service;

/**
 * 대화방에 메시지가 생겼다. 사람이 보낸 메시지와 안내 메시지 모두다. 만든 트랜잭션이 커밋된 뒤에 낸다.
 * 실시간 소식(06 채팅 ③의 이벤트 연결)이 받아 그 대화방에 있는 사람들에게 {@code CHAT_MESSAGE_CREATED}로 보낸다.
 *
 * @param chatRoomId 메시지가 생긴 대화방
 * @param messageId 생긴 메시지
 */
public record ChatMessageCreatedEvent(Long chatRoomId, Long messageId) {
}
