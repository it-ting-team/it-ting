package com.itting.chat.domain;

/**
 * 메시지의 종류({@code chat_message.type}). {@link #TEXT}가 아닌 것은 일이 일어날 때 서버가 넣는 안내 메시지다.
 */
public enum ChatMessageType {

	/** 사람이 보낸 메시지. */
	TEXT,
	/** 대화 요청을 수락해 1:1 대화방이 열렸다. 보낸 사람은 수락한 사람이다. */
	ACCEPTED,
	/** 모임 대화방에 들어왔다. */
	JOINED,
	/** 대화방에서 나갔다. */
	LEFT,
	/** 서비스에서 탈퇴했다. */
	WITHDRAWN

}
