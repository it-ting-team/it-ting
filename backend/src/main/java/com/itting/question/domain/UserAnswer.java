package com.itting.question.domain;

import java.io.Serializable;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 사람이 고른 자기소개 질문과 답변({@code user_answer}). 한 사람은 서로 다른 질문 3개에 답하고,
 * {@code slot}(1 ~ 3)이 자기소개에서 보이는 자리다. 저장은 03 온보딩 · 04 내 정보 수정이 한다.
 */
@Entity
@Table(name = "user_answer")
@IdClass(UserAnswer.Key.class)
public class UserAnswer extends BaseTimeEntity {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "slot")
	private byte slot;

	@Column(name = "intro_question_id", nullable = false)
	private Long introQuestionId;

	@Column(nullable = false, length = 50)
	private String answer;

	protected UserAnswer() {
	}

	/**
	 * @param slot 자기소개에서 보이는 자리. 1 ~ 3
	 * @param answer 10 ~ 50자
	 */
	public static UserAnswer create(Long userId, int slot, Long introQuestionId, String answer) {
		UserAnswer userAnswer = new UserAnswer();
		userAnswer.userId = userId;
		userAnswer.slot = (byte) slot;
		userAnswer.introQuestionId = introQuestionId;
		userAnswer.answer = answer;
		return userAnswer;
	}

	public Long getUserId() {
		return userId;
	}

	public int getSlot() {
		return slot;
	}

	public Long getIntroQuestionId() {
		return introQuestionId;
	}

	public String getAnswer() {
		return answer;
	}

	/** 복합 기본키 {@code (user_id, slot)}. */
	public record Key(Long userId, byte slot) implements Serializable {
	}

}
