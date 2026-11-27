package com.itting.chat.dto;

/**
 * 실시간 소식 {@code CHAT_MESSAGE_CREATED}의 값({@code docs/api/06-chat.md}의 {@code GET /api/v1/me/event-stream}).
 *
 * @param chatRoomId 메시지가 생긴 대화방의 id
 * @param message 생긴 메시지. 메시지 조회 · 보내기의 응답과 같은 모양이다
 * @param unreadCount 이 소식을 받는 사람의, 그 대화방의 안 읽은 수. 받는 사람마다 다르다
 */
public record ChatMessageCreatedEventResponse(Long chatRoomId, ChatMessageResponse message, long unreadCount) {
}
