package com.itting.notification.domain;

/**
 * 알림을 눌렀을 때 갈 곳의 종류({@code notification.target_type}).
 */
public enum NotificationTargetType {

	/** 모임 상세. id는 모임의 id. */
	MEETUP,
	/** 채팅의 대화방. id는 대화방의 id. */
	CHAT_ROOM,
	/** 채팅 › 요청 › 받은 요청. id는 대화 요청의 id. */
	CHAT_REQUEST,
	/** 커뮤니티의 글 상세. id는 글의 id. 댓글 알림도 그 글로 간다. */
	POST,
	/** 내 땅굴의 방명록 전체 보기. id는 없다. */
	GUESTBOOK,
	/** 다른 사람의 땅굴(방문자 뷰). id는 땅굴 주인의 계정 id. */
	BURROW,
	/** 공지사항 상세. id는 공지의 id. */
	NOTICE

}
