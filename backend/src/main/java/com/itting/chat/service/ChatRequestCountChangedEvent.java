package com.itting.chat.service;

/**
 * 이 사람이 받은 요청 수가 바뀌었다(새 요청을 받았거나, 보낸 사람이 취소했거나, 내가 수락하거나 거절했다). 바꾼 트랜잭션이
 * 커밋된 뒤에 낸다. 실시간 소식(06 채팅 ③의 이벤트 연결)이 받아 {@code CHAT_REQUEST_COUNT_CHANGED}로 보낸다. 값은
 * {@link ChatRequestQueryService#countReceivedPending(Long)}로 계산한다.
 *
 * @param userId 요청을 받은 사람
 */
public record ChatRequestCountChangedEvent(Long userId) {
}
