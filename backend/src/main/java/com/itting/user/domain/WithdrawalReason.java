package com.itting.user.domain;

/** 탈퇴 사유({@code user_withdrawal.reason}). 글은 프론트엔드가 갖는다. */
public enum WithdrawalReason {

	/** 자주 쓰지 않아요 */
	NOT_USING,

	/** SSAFY 과정이 끝났어요 */
	COURSE_ENDED,

	/** 맞는 사람이나 모임을 찾지 못했어요 */
	NO_MATCH,

	/** 쓰기 불편해요 */
	INCONVENIENT,

	/** 불쾌한 일을 겪었어요 */
	UNPLEASANT,

	/** 기타 */
	ETC

}
