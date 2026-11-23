package com.itting.community.domain;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 글의 제목과 본문({@code post_version}). 고칠 때마다 한 줄씩 쌓고 번호가 가장 큰 줄을 보여 준다.
 * 한 번 쓴 줄은 고치지 않는다. 고치기 전의 내용은 관리자가 신고를 살필 때 본다.
 */
@Entity
@Table(name = "post_version")
public class PostVersion extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false, updatable = false)
	private Long postId;

	@Column(nullable = false, updatable = false)
	private int version;

	@Column(nullable = false, updatable = false, length = 50)
	private String title;

	@Column(nullable = false, updatable = false, length = 1000)
	private String content;

	protected PostVersion() {
	}

	/**
	 * @param version 글 안에서의 번호. 1부터
	 */
	public static PostVersion create(Long postId, int version, String title, String content) {
		PostVersion postVersion = new PostVersion();
		postVersion.postId = postId;
		postVersion.version = version;
		postVersion.title = title;
		postVersion.content = content;
		return postVersion;
	}

	/** 제목이나 본문이 이 버전과 다른지. */
	public boolean differsFrom(String title, String content) {
		return !this.title.equals(title) || !this.content.equals(content);
	}

	public Long getId() {
		return id;
	}

	public Long getPostId() {
		return postId;
	}

	public int getVersion() {
		return version;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

}
