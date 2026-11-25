package com.itting.community.domain;

import com.itting.meetup.domain.MeetupLinkScope;

/** 글의 카테고리({@code post.category}). */
public enum PostCategory {
	/** 모임 홍보. 연결한 모임이 꼭 있어야 한다. */
	PROMOTION,
	/** 모임 후기. 연결한 모임이 꼭 있어야 한다. */
	REVIEW,
	/** 자유. 모임은 연결해도 되고 안 해도 된다. */
	FREE;

	/** 모임을 꼭 연결해야 하는 카테고리인지. */
	public boolean requiresMeetup() {
		return this != FREE;
	}

	/** 이 카테고리의 글에 연결할 수 있는 모임을 모임 영역의 기준으로 바꾼 것. */
	public MeetupLinkScope linkScope() {
		return switch (this) {
			case PROMOTION -> MeetupLinkScope.HOSTED_OPEN;
			case REVIEW -> MeetupLinkScope.JOINED_PAST;
			case FREE -> MeetupLinkScope.JOINED;
		};
	}
}
