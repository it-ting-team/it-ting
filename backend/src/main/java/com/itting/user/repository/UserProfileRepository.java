package com.itting.user.repository;

import java.util.Collection;
import java.util.List;
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

	/**
	 * 사용자 표시를 만들 값을 계정 · 프로필 · 캠퍼스-층 · 캠퍼스를 이어 한 번에 읽는다. 탈퇴해 프로필이 없는 계정도 결과에 있다.
	 * 없는 계정 id는 결과에 없다.
	 */
	@Query("""
			SELECT new com.itting.user.repository.UserSummaryRow(
			    u.id, u.status, p.nickname, p.profileImageUrl, p.moleHead, p.moleFace, p.moleNeck, p.moleHand,
			    c.name, p.generation, f.floor)
			FROM User u
			LEFT JOIN UserProfile p ON p.userId = u.id
			LEFT JOIN CampusFloor f ON f.id = p.campusFloorId
			LEFT JOIN Campus c ON c.id = f.campusId
			WHERE u.id IN :userIds
			""")
	List<UserSummaryRow> findSummaryRows(@Param("userIds") Collection<Long> userIds);

}
