package com.itting.question.service;

import java.util.List;

import com.itting.question.domain.IntroQuestion;
import com.itting.question.domain.IntroQuestionType;
import com.itting.question.dto.IntroQuestionListResponse;
import com.itting.question.dto.IntroQuestionListResponse.IntroQuestionResponse;
import com.itting.question.repository.IntroQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IntroQuestionService {

	private final IntroQuestionRepository introQuestionRepository;

	public IntroQuestionService(IntroQuestionRepository introQuestionRepository) {
		this.introQuestionRepository = introQuestionRepository;
	}

	/**
	 * 질문을 id 순으로 모두 준다. 질문은 숨기거나 지우지 않는다.
	 *
	 * @param type 이 종류만 준다. null이면 두 종류를 모두 준다
	 */
	public IntroQuestionListResponse getIntroQuestions(IntroQuestionType type) {
		List<IntroQuestion> questions = type == null
				? introQuestionRepository.findAllByOrderByIdAsc()
				: introQuestionRepository.findAllByTypeOrderByIdAsc(type);
		List<IntroQuestionResponse> items = questions.stream()
				.map(question -> new IntroQuestionResponse(question.getId(), question.getType(),
						question.getInterestCategoryId(), question.getContent()))
				.toList();
		return new IntroQuestionListResponse(items);
	}

}
