package com.itting.burrow.repository;

import java.util.Optional;

import com.itting.burrow.domain.BurrowEntrance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BurrowEntranceRepository extends JpaRepository<BurrowEntrance, Long> {

	/** 이 사람의 땅굴 입구가 놓인 학기. 땅굴이 없으면 빈 값. */
	@Query("""
			SELECT e.termId FROM BurrowEntrance e, Burrow b
			WHERE b.id = e.burrowId AND b.userId = :userId
			""")
	Optional<Long> findTermIdByUserId(@Param("userId") Long userId);

	/** 땅굴의 입구. 땅굴 하나에 하나다. */
	Optional<BurrowEntrance> findByBurrowId(Long burrowId);

	/**
	 * 캠퍼스-층 행을 트랜잭션이 끝날 때까지 잠근다. 같은 층에 자리 번호를 주는 일(온보딩 확정, 이사)을 한 줄로 세운다.
	 *
	 * @return 잠근 캠퍼스-층의 id. 없는 캠퍼스-층이면 null
	 */
	@Query(value = "SELECT id FROM campus_floor WHERE id = :campusFloorId FOR UPDATE", nativeQuery = true)
	Long lockCampusFloor(@Param("campusFloorId") Long campusFloorId);

	/**
	 * 들판에서 가장 큰 자리 번호. 입구가 없으면 0이다. {@link #lockCampusFloor}로 잠근 뒤 부른다.
	 * 잠그는 읽기({@code FOR UPDATE})라 트랜잭션이 처음 읽은 때가 아니라 지금 커밋된 값을 본다.
	 */
	@Query(value = """
			SELECT COALESCE(MAX(slot), 0) FROM burrow_entrance
			WHERE term_id = :termId AND campus_floor_id = :campusFloorId
			FOR UPDATE
			""", nativeQuery = true)
	int findMaxSlotForUpdate(@Param("termId") Long termId, @Param("campusFloorId") Long campusFloorId);

}
