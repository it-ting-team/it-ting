package com.itting.chat.domain;

/**
 * 대화방의 나가기 버튼이 지금 하는 일({@code docs/api/06-chat.md}의 "대화방에서 나가기"). 프론트엔드가 이 값으로 버튼의 모습과
 * 부를 API를 정한다.
 */
public enum ChatLeaveType {

	/** 채팅의 나가기 API를 부른다. 1:1 대화방, 그리고 취소됐거나 끝난 모임의 대화방이다. */
	LEAVE_ROOM,

	/** 모임의 참여 취소 API를 부른다. 끝나지 않은 모임의 참여자이고 시작 24시간 전이 지나지 않았다. */
	CANCEL_PARTICIPATION,

	/** 나갈 수 없다. 끝나지 않은 모임의 주최자이거나, 시작 24시간 전이 지난 참여자다. */
	NOT_ALLOWED

}
