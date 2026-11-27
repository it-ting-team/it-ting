package com.itting.question.service;

import com.itting.question.repository.UserAnswerRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴의 자기소개 질문 영역 정리. 탈퇴 트랜잭션 안에서만 부른다. 내 자기소개 답변을 지운다. */
@Component
public class QuestionWithdrawalCleaner {

	private final UserAnswerRepository userAnswers;

	public QuestionWithdrawalCleaner(UserAnswerRepository userAnswers) {
		this.userAnswers = userAnswers;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void clean(Long userId) {
		userAnswers.deleteAllByUserIdNow(userId);
	}

}
