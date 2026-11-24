package com.itting.community.domain;

import java.io.Serializable;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 댓글과 답글에 누른 좋아요({@code comment_like}). 켜고 끄는 관계이고 끄면 줄을 지운다.
 * 동시에 두 번 눌러도 한 줄이게, 줄은 엔티티를 저장하지 않고 저장소의 {@code insertIfAbsent}로만 만든다.
 */
@Entity
@Table(name = "comment_like")
@IdClass(CommentLike.Key.class)
public class CommentLike extends BaseCreatedTimeEntity {

	@Id
	@Column(name = "comment_id")
	private Long commentId;

	@Id
	@Column(name = "user_id")
	private Long userId;

	protected CommentLike() {
	}

	public Long getCommentId() {
		return commentId;
	}

	public Long getUserId() {
		return userId;
	}

	/** 복합 기본키 {@code (comment_id, user_id)}. */
	public record Key(Long commentId, Long userId) implements Serializable {
	}

}
