package com.itting.chat.dto;

/**
 * {@code CHAT_REQUEST_ALREADY_ACCEPTED}의 {@code error.detail}. 취소하려는 사이에 상대가 수락한 요청이다.
 * 프론트엔드가 카드를 빼고 대화 목록을 다시 부른다.
 *
 * @param chatRoomId 상대가 수락해서 만든 1:1 대화방의 id
 */
public record ChatRequestAcceptedDetailResponse(Long chatRoomId) {
}
