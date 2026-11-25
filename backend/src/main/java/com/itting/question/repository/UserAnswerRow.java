package com.itting.question.repository;

/**
 * 한 사람의 질문과 답변 한 줄.
 *
 * @param slot 자기소개에서 보이는 자리. 1 ~ 3
 * @param question 질문 문구
 */
public record UserAnswerRow(Long userId, byte slot, Long introQuestionId, String question, String answer) {
}
