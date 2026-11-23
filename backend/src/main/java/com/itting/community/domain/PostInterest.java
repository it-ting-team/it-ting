package com.itting.community.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 글에 붙인 관심사 대분류 태그({@code post_interest}). 글마다 0 ~ 5개. 글과 함께 생기므로 시각 칼럼이 없다.
 */
@Entity
@Table(name = "post_interest")
@IdClass(PostInterest.Key.class)
public class PostInterest {

	@Id
	@Column(name = "post_id")
	private Long postId;

	@Id
	@Column(name = "interest_category_id")
	private Long interestCategoryId;

	protected PostInterest() {
	}

	public static PostInterest create(Long postId, Long interestCategoryId) {
		PostInterest postInterest = new PostInterest();
		postInterest.postId = postId;
		postInterest.interestCategoryId = interestCategoryId;
		return postInterest;
	}

	public Long getPostId() {
		return postId;
	}

	public Long getInterestCategoryId() {
		return interestCategoryId;
	}

	/** 복합 기본키 {@code (post_id, interest_category_id)}. */
	public record Key(Long postId, Long interestCategoryId) implements Serializable {
	}

}
