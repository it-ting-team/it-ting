package com.itting.community.domain;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 댓글의 본문({@code comment_version}). 고칠 때마다 한 줄씩 쌓고 번호가 가장 큰 줄을 보여 준다.
 * 한 번 쓴 줄은 고치지 않는다.
 */
@Entity
@Table(name = "comment_version")
public class CommentVersion extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "comment_id", nullable = false, updatable = false)
	private Long commentId;

	@Column(nullable = false, updatable = false)
	private int version;

	@Column(nullable = false, updatable = false, length = 300)
	private String content;

	protected CommentVersion() {
	}

	/**
	 * @param version 댓글 안에서의 번호. 1부터
	 */
	public static CommentVersion create(Long commentId, int version, String content) {
		CommentVersion commentVersion = new CommentVersion();
		commentVersion.commentId = commentId;
		commentVersion.version = version;
		commentVersion.content = content;
		return commentVersion;
	}

	/** 본문이 이 버전과 다른지. */
	public boolean differsFrom(String content) {
		return !this.content.equals(content);
	}

	public Long getId() {
		return id;
	}

	public Long getCommentId() {
		return commentId;
	}

	public int getVersion() {
		return version;
	}

	public String getContent() {
		return content;
	}

}
