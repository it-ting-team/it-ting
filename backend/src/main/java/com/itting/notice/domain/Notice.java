package com.itting.notice.domain;

import java.time.Instant;

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
 * 관리자가 올린 공지({@code notice}). 사용자는 읽기만 한다. 지우면 {@code deleted_at}을 채우고 행은 남긴다.
 * 올리고 고치는 API는 11 운영 · 관리자에서 만든다.
 */
@Entity
@Table(name = "notice")
public class Notice extends BaseTimeEntity {

	/** 제목의 최대 길이. 칼럼 {@code title}의 길이와 같다(KAN-58). */
	public static final int TITLE_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NoticeCategory category;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	/** 줄바꿈이 있는 글. 꾸미는 문법(HTML, 마크다운)은 쓰지 않는다. */
	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Notice() {
	}

	/** 새 공지. 올리는 API는 11 운영 · 관리자에서 만든다. 그 전에는 테스트와 로컬 데이터가 쓴다. */
	public static Notice create(NoticeCategory category, String title, String content) {
		Notice notice = new Notice();
		notice.category = category;
		notice.title = title;
		notice.content = content;
		return notice;
	}

	/** 지운다. 이 뒤로 목록에 나오지 않고 상세는 {@code NOTICE_NOT_FOUND}다. */
	public void delete(Instant now) {
		this.deletedAt = now;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public Long getId() {
		return id;
	}

	public NoticeCategory getCategory() {
		return category;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
