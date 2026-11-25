package com.itting.meetup.domain;

/**
 * 어떤 모임을 다른 영역(글)에 연결할 수 있는지의 기준. 연결하는 쪽이 자기 카테고리를 이 값으로 바꿔 넘긴다.
 * 취소된 모임과 삭제된 모임은 어느 기준에서도 고를 수 없다.
 */
public enum MeetupLinkScope {

	/** 내가 연 모임 가운데 모집 중인 것({@link RecruitStatus#OPEN}). 시작이 가까운 것부터. */
	HOSTED_OPEN,

	/** 내가 열었거나 참여가 확정된 모임 가운데 지난 것({@link RecruitStatus#PAST}). 시작이 최근인 것부터. */
	JOINED_PAST,

	/** 내가 열었거나 참여가 확정된 모임. 지난 모임도 된다. 시작이 최근인 것부터. */
	JOINED

}
