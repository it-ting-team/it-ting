package com.itting.chat.domain;

/**
 * 메시지와 공지의 길이 · 개수({@code docs/api/06-chat.md}의 "글자 수와 첨부"). 글자 수는 유니코드 문자 단위로 센다.
 */
public final class ChatRules {

	/** 메시지 본문의 최대 글자 수. */
	public static final int MESSAGE_MAX = 1000;

	/** 메시지 하나에 붙이는 사진의 최대 수. */
	public static final int IMAGE_MAX = 3;

	/** 공지의 최대 글자 수. */
	public static final int NOTICE_MAX = 200;

	/** 메시지를 한 번에 받는 개수의 기본값과 최댓값. */
	public static final int PAGE_DEFAULT = 30;

	public static final int PAGE_MAX = 100;

	private ChatRules() {
	}

}
