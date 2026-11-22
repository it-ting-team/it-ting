package com.itting.user.repository;

import java.util.Optional;

import com.itting.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** 로그인 이메일로 찾는다. 대소문자는 가리지 않는다(DB의 정렬 규칙이 utf8mb4_0900_ai_ci). */
	Optional<User> findByEmail(String email);

	/** 이메일로 가입한 계정이 있는지. 탈퇴한 계정은 이메일을 비워 두므로 걸리지 않는다. */
	boolean existsByEmail(String email);

	/** 이 Mattermost ID로 SSAFY 인증을 마친 계정이 있는지. 탈퇴한 계정은 비워 둔다. */
	boolean existsByMattermostId(String mattermostId);

}
