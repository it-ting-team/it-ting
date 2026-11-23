package com.itting.guestbook.domain;

/** 방명록 글의 공개 상태({@code guestbook_entry.status}). */
public enum GuestbookEntryStatus {

	/** 보이는 글. */
	PUBLIC,

	/** 서로 다른 3명이 신고해 가려진 글. 목록과 글 수에서 빼고, 지울 수도 없다. */
	HIDDEN

}
