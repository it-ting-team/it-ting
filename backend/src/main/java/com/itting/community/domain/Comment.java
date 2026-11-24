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
 * 글에 달린 댓글과 한 단계 답글({@code comment}). 본문은 {@link CommentVersion}에 있다. 상태는 글과 같은
 * {@link PostStatus}를 쓴다. 지워도 줄을 남긴다.
 *
 * <p>{@code parentId}는 답글이 속한 맨 위 댓글이다. 답글에 다시 답해도 같은 댓글 아래에 달고, 누구에게 답했는지는
 * {@code mentionUserId}로 남긴다.
 */
@Entity
@Table(name = "comment")
public class Comment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false, updatable = false)
	private Long postId;

	@Column(name = "author_id", nullable = false, updatable = false)
	private Long authorId;

	@Column(name = "parent_id", updatable = false)
	private Long parentId;

	@Column(name = "mention_user_id", updatable = false)
	private Long mentionUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PostStatus status;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Comment() {
	}

	/** 글에 직접 다는 댓글. */
	public static Comment create(Long postId, Long authorId) {
		return reply(postId, authorId, null, null);
	}

	/**
	 * 댓글에 다는 답글.
	 *
	 * @param parentId 답글이 속한 맨 위 댓글
	 * @param mentionUserId 답글에 다시 답했을 때 그 답글의 작성자. 댓글에 답했으면 null
	 */
	public static Comment reply(Long postId, Long authorId, Long parentId, Long mentionUserId) {
		Comment comment = new Comment();
		comment.postId = postId;
		comment.authorId = authorId;
		comment.parentId = parentId;
		comment.mentionUserId = mentionUserId;
		comment.status = PostStatus.PUBLIC;
		return comment;
	}

	/** 지운다. 달린 답글을 지우는 일은 서비스가 함께 한다. */
	public void delete(Instant now) {
		this.status = PostStatus.DELETED;
		this.deletedAt = now;
	}

	public boolean isWrittenBy(Long userId) {
		return authorId.equals(userId);
	}

	/** 답글인지(맨 위 댓글이 아닌지). */
	public boolean isReply() {
		return parentId != null;
	}

	public boolean isPublic() {
		return status == PostStatus.PUBLIC;
	}

	public boolean isHidden() {
		return status == PostStatus.HIDDEN;
	}

	public boolean isDeleted() {
		return status == PostStatus.DELETED;
	}

	public Long getId() {
		return id;
	}

	public Long getPostId() {
		return postId;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public Long getParentId() {
		return parentId;
	}

	public Long getMentionUserId() {
		return mentionUserId;
	}

	public PostStatus getStatus() {
		return status;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
