package com.itting.community.domain;

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
 * 커뮤니티 글({@code post}). 작성자 · 카테고리 · 상태 · 조회수만 두고, 제목과 본문은 {@link PostVersion}에 있다.
 * 지워도 줄을 남긴다(삭제된 글 안내, 답글 · 신고의 참조).
 */
@Entity
@Table(name = "post")
public class Post extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "author_id", nullable = false, updatable = false)
	private Long authorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PostCategory category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PostStatus status;

	/** 조회수. 올릴 때는 엔티티를 고치지 않고 SQL로 1을 더한다(동시에 열어도 빠지지 않게). */
	@Column(name = "view_count", nullable = false, updatable = false)
	private int viewCount;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Post() {
	}

	public static Post create(Long authorId, PostCategory category) {
		Post post = new Post();
		post.authorId = authorId;
		post.category = category;
		post.status = PostStatus.PUBLIC;
		post.viewCount = 0;
		return post;
	}

	public void changeCategory(PostCategory category) {
		this.category = category;
	}

	/** 지운다. 달린 댓글을 지우는 일은 서비스가 함께 한다. */
	public void delete(Instant now) {
		this.status = PostStatus.DELETED;
		this.deletedAt = now;
	}

	public boolean isWrittenBy(Long userId) {
		return authorId.equals(userId);
	}

	public boolean isPublic() {
		return status == PostStatus.PUBLIC;
	}

	public boolean isDeleted() {
		return status == PostStatus.DELETED;
	}

	public boolean isHidden() {
		return status == PostStatus.HIDDEN;
	}

	public Long getId() {
		return id;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public PostCategory getCategory() {
		return category;
	}

	public PostStatus getStatus() {
		return status;
	}

	public int getViewCount() {
		return viewCount;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
