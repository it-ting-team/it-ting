package com.itting.question.repository;

import java.util.List;

import com.itting.question.domain.IntroQuestion;
import com.itting.question.domain.IntroQuestionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntroQuestionRepository extends JpaRepository<IntroQuestion, Long> {

	/** 모든 질문을 id 순으로. 순서 칼럼이 없어 id 순이다. */
	List<IntroQuestion> findAllByOrderByIdAsc();

	/** 한 종류의 질문을 id 순으로. */
	List<IntroQuestion> findAllByTypeOrderByIdAsc(IntroQuestionType type);

}
