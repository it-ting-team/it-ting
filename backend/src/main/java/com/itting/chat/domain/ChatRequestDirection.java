package com.itting.chat.domain;

/**
 * 대화 요청 목록의 방향({@code GET /api/v1/me/chat-requests}의 {@code direction}).
 */
public enum ChatRequestDirection {

	/** 내가 받은 요청. */
	RECEIVED,
	/** 내가 보낸 요청. */
	SENT

}
