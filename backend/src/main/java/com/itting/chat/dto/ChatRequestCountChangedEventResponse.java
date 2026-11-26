package com.itting.chat.dto;

/**
 * 실시간 소식 {@code CHAT_REQUEST_COUNT_CHANGED}의 값({@code docs/api/06-chat.md}의 {@code GET /api/v1/me/event-stream}).
 *
 * @param receivedCount 바뀐 뒤의 받은 요청 수. 요청 수 API의 {@code receivedCount}와 같다
 */
public record ChatRequestCountChangedEventResponse(long receivedCount) {
}
