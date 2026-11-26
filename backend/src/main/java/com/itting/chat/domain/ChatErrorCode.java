package com.itting.chat.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 채팅 영역의 에러 코드. 대화 요청 보내기 · 수락 · 거절 · 취소와 대화방 · 메시지 · 읽음 · 나가기 · 공지가 쓴다.
 */
public enum ChatErrorCode implements ErrorCode {

	/** 없는 요청, 내가 받지(보내지) 않은 요청, 취소됐거나 만료됐거나 이미 거절한 요청. */
	CHAT_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "대화 요청을 찾을 수 없습니다."),

	/** 내가 그 사람에게 보낸 살아 있는 요청이 있다. */
	CHAT_REQUEST_ALREADY_SENT(HttpStatus.CONFLICT, "이미 대화 요청을 보냈습니다."),

	/** 그 사람이 나에게 보낸 요청에 내가 아직 답하지 않았다. */
	CHAT_REQUEST_ALREADY_RECEIVED(HttpStatus.CONFLICT, "상대가 이미 대화 요청을 보냈습니다."),

	/** 상대가 이미 수락한 요청을 취소하려 했다. {@code detail.chatRoomId}가 만들어진 대화방의 id다. */
	CHAT_REQUEST_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "상대가 이미 수락한 요청입니다."),

	/** 두 사람이 모두 나가지 않은 1:1 대화방이 이미 있다. */
	CHAT_ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 대화 중인 사람입니다."),

	/** 없는 대화방, 내가 들어가 있지 않거나 나간 대화방, 삭제된 모임의 대화방. 서로 구분하지 않는다. */
	CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "대화방을 찾을 수 없습니다."),

	/** 상대가 나갔거나 탈퇴한 1:1 대화방에 메시지를 보내려 했다. */
	CHAT_ROOM_READ_ONLY(HttpStatus.CONFLICT, "상대가 나가 메시지를 보낼 수 없는 대화방입니다."),

	/** 취소되지도 끝나지도 않은 모임의 대화방에서 나가려 했다. */
	CHAT_ROOM_LEAVE_NOT_ALLOWED(HttpStatus.CONFLICT, "끝나지 않은 모임의 대화방에서는 나갈 수 없습니다.");

	private final HttpStatus status;

	private final String message;

	ChatErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public HttpStatus status() {
		return status;
	}

	@Override
	public String message() {
		return message;
	}

}
