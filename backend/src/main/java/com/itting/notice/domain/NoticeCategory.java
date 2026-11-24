package com.itting.notice.domain;

/**
 * 공지의 분류({@code notice.category}). 목록은 분류로 거르지 않고 모두 준다.
 */
public enum NoticeCategory {

	/** 일반 공지. */
	NOTICE,
	/** 업데이트. */
	UPDATE,
	/** 이벤트. */
	EVENT

}
