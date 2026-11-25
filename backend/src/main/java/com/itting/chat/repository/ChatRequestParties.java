package com.itting.chat.repository;

/**
 * 대화 요청을 주고받는 두 사람. 보낸 사람과 받은 사람은 바뀌지 않으므로, 요청을 잠그기 전에 두 사람을 먼저 잠그려고 읽는다.
 */
public record ChatRequestParties(Long senderId, Long receiverId) {
}
