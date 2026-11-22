package com.itting.user.repository;

import java.util.Optional;

import com.itting.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** 로그인 이메일로 찾는다. 대소문자는 가리지 않는다(DB의 정렬 규칙이 utf8mb4_0900_ai_ci). */
	Optional<User> findByEmail(String email);

}
