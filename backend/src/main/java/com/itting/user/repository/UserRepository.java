package com.itting.user.repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.itting.user.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	/** 로그인 이메일로 찾는다. 대소문자는 가리지 않는다(DB의 정렬 규칙이 utf8mb4_0900_ai_ci). */
	Optional<User> findByEmail(String email);

	/** 계정 행을 트랜잭션이 끝날 때까지 잠그고 읽는다. 같은 계정의 요청이 동시에 상태를 바꾸지 못하게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM User u WHERE u.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);

	/**
	 * 두 계정 행을 id가 작은 것부터 트랜잭션이 끝날 때까지 잠그고 읽는다. 대화 요청을 보내고 · 수락하고 · 거절하고 · 취소하는
	 * 트랜잭션이 같은 두 사람을 서로 반대 순서로 잠가 교착하지 않게 하고, 두 사람의 일이 한 번에 하나씩만 처리되게 한다
	 * ({@code docs/db/chat.md}). 없는 계정은 결과에 없다.
	 *
	 * <p>잠그는 일이 트랜잭션의 첫 SQL이어야 한다. 앞서 일반 조회가 있으면 그 시점의 스냅샷으로 다시 읽게 되어,
	 * 기다리는 사이에 다른 트랜잭션이 커밋한 것이 보이지 않는다.
	 *
	 * @return 계정 id → 잠근 계정. 잠근 순서대로다
	 */
	default Map<Long, User> findBothForUpdate(Long firstId, Long secondId) {
		Map<Long, User> locked = new LinkedHashMap<>();
		for (Long id : Stream.of(firstId, secondId).sorted().toList()) {
			findByIdForUpdate(id).ifPresent(user -> locked.put(id, user));
		}
		return locked;
	}

	/** 계정 상태 확인 뒤 참조 행을 만드는 동안 상태 변경을 기다리게 한다. */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("SELECT u FROM User u WHERE u.id = :id")
	Optional<User> findByIdForShare(@Param("id") Long id);

	/** 이메일로 가입한 계정이 있는지. 탈퇴한 계정은 이메일을 비워 두므로 걸리지 않는다. */
	boolean existsByEmail(String email);

	/** 이 Mattermost ID로 SSAFY 인증을 마친 계정이 있는지. 탈퇴한 계정은 비워 둔다. */
	boolean existsByMattermostId(String mattermostId);

}
