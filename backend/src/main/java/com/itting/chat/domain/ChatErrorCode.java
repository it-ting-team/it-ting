package com.itting.chat.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 채팅 영역의 에러 코드. 대화 요청 보내기 · 수락 · 거절 · 취소가 쓴다.
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
	CHAT_ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 대화 중인 사람입니다.");

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
