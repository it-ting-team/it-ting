package com.itting.guestbook.domain;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 방문자가 다른 사람의 땅굴에 남긴 방명록 글({@code guestbook_entry}). 고칠 수 없고, 지우면 행을 지운다.
 * 신고로 가려지면 {@link GuestbookEntryStatus#HIDDEN}이 되어 누구에게도 보이지 않는다.
 */
@Entity
@Table(name = "guestbook_entry")
public class GuestbookEntry extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 글을 받은 땅굴. */
	@Column(name = "burrow_id", nullable = false, updatable = false)
	private Long burrowId;

	/** 쓴 사람. 탈퇴해도 글은 남고 이 값도 그대로다. */
	@Column(name = "author_id", nullable = false, updatable = false)
	private Long authorId;

	@Column(nullable = false, length = 50, updatable = false)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GuestbookEntryStatus status;

	protected GuestbookEntry() {
	}

	/** 공개 상태의 새 글. 쓰는 API는 05 탐색 · 방문에서 만든다. */
	public static GuestbookEntry create(Long burrowId, Long authorId, String content) {
		GuestbookEntry entry = new GuestbookEntry();
		entry.burrowId = burrowId;
		entry.authorId = authorId;
		entry.content = content;
		entry.status = GuestbookEntryStatus.PUBLIC;
		return entry;
	}

	/** 신고로 가린다. 누구에게도 보이지 않고 글 수에서도 빠진다. */
	public void hide() {
		this.status = GuestbookEntryStatus.HIDDEN;
	}

	public boolean isHidden() {
		return status == GuestbookEntryStatus.HIDDEN;
	}

	public Long getId() {
		return id;
	}

	public Long getBurrowId() {
		return burrowId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public String getContent() {
		return content;
	}

	public GuestbookEntryStatus getStatus() {
		return status;
	}

}
