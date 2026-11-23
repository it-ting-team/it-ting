package com.itting.question.repository;

import com.itting.question.domain.UserAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAnswerRepository extends JpaRepository<UserAnswer, UserAnswer.Key> {

}
