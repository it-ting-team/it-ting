package com.itting.question.domain;

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
 * 자기소개 질문({@code intro_question}). 운영자가 더하거나 고치고, 숨기거나 지우지 않는다.
 * 관심사 질문은 대분류가 꼭 있고 TMI 질문은 비어 있다(DB의 CHECK가 지킨다).
 */
@Entity
@Table(name = "intro_question")
public class IntroQuestion extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IntroQuestionType type;

	@Column(name = "interest_category_id")
	private Long interestCategoryId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	protected IntroQuestion() {
	}

	public Long getId() {
		return id;
	}

	public IntroQuestionType getType() {
		return type;
	}

	public Long getInterestCategoryId() {
		return interestCategoryId;
	}

	public String getContent() {
		return content;
	}

}
