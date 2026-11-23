package com.itting.user.repository;

import java.util.Optional;

import com.itting.user.domain.UserProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

	/**
	 * 다른 사람이 이 닉네임을 쓰고 있는지. 영어 대소문자는 가리지 않는다(DB의 정렬 규칙이 utf8mb4_0900_ai_ci).
	 *
	 * @param userId 빼고 볼 사람. 내 지금 닉네임은 겹친 것으로 보지 않는다
	 */
	boolean existsByNicknameAndUserIdNot(String nickname, Long userId);

	/** 프로필 행을 트랜잭션이 끝날 때까지 잠그고 읽는다. 같은 사람의 내 정보 저장이 동시에 오면 차례로 처리한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM UserProfile p WHERE p.userId = :userId")
	Optional<UserProfile> findByIdForUpdate(@Param("userId") Long userId);

}
