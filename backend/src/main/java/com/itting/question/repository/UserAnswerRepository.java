package com.itting.question.repository;

import java.util.List;

import com.itting.question.domain.UserAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAnswerRepository extends JpaRepository<UserAnswer, UserAnswer.Key> {

	/** 한 사람의 질문과 답변을 자리 순으로, 질문 문구와 함께. */
	@Query("""
			SELECT new com.itting.question.repository.UserAnswerRow(a.slot, a.introQuestionId, q.content, a.answer)
			FROM UserAnswer a
			JOIN IntroQuestion q ON q.id = a.introQuestionId
			WHERE a.userId = :userId
			ORDER BY a.slot
			""")
	List<UserAnswerRow> findRowsByUserId(@Param("userId") Long userId);

	/**
	 * 한 사람의 답변을 모두 바로 지운다. 내 정보 수정이 답변을 새로 넣기 전에 부른다.
	 * 한 줄씩 고치면 두 자리의 질문을 서로 바꿀 때 {@code UNIQUE(user_id, intro_question_id)}에 걸리기 때문이다.
	 * 지우기 전에 미뤄 둔 변경을 DB에 쓰고, 지운 뒤 영속성 컨텍스트를 비운다. 지운 답변이 컨텍스트에 남아 있으면 같은 자리의
	 * 새 답변을 저장할 때 INSERT 대신 UPDATE가 나가기 때문이다. 부른 쪽은 이 뒤에 앞서 읽은 엔티티를 고치지 않는다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM UserAnswer a WHERE a.userId = :userId")
	void deleteAllByUserIdNow(@Param("userId") Long userId);

}
