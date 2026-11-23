package com.itting.user.repository;

import com.itting.user.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

	/**
	 * 다른 사람이 이 닉네임을 쓰고 있는지. 영어 대소문자는 가리지 않는다(DB의 정렬 규칙이 utf8mb4_0900_ai_ci).
	 *
	 * @param userId 빼고 볼 사람. 내 지금 닉네임은 겹친 것으로 보지 않는다
	 */
	boolean existsByNicknameAndUserIdNot(String nickname, Long userId);

}
