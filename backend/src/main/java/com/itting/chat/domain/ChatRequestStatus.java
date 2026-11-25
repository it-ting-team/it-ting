package com.itting.chat.domain;

/**
 * 대화 요청의 실제 처리 상태({@code chat_request.status}). 만료는 저장하지 않고 보낸 시각에서 계산한다
 * ({@link ChatRequest#isAlive(java.time.Instant)}).
 */
public enum ChatRequestStatus {

	/** 받은 사람이 아직 답하지 않았다. */
	PENDING,
	/** 받은 사람이 수락해 1:1 대화방이 만들어졌다. */
	ACCEPTED,
	/** 받은 사람이 거절했다. 보낸 사람에게는 드러나지 않고, 만료될 때까지 {@link #PENDING}처럼 보인다. */
	REJECTED,
	/** 보낸 사람이 취소했다. */
	CANCELED

}
