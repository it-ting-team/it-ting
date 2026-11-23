package com.itting.community.domain;

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
}
