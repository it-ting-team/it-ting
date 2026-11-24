package com.itting.burrow.repository;

import java.time.Instant;
import java.util.List;

import com.itting.burrow.domain.RegularBurrow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RegularBurrowRepository extends JpaRepository<RegularBurrow, RegularBurrow.Key> {

	@Modifying
	@Query(value = """
			INSERT IGNORE INTO regular_burrow (user_id, burrow_id, created_at)
			VALUES (:userId, :burrowId, :now)
			""", nativeQuery = true)
	void insertIfAbsent(Long userId, Long burrowId, Instant now);

	@Modifying
	@Query("DELETE FROM RegularBurrow r WHERE r.userId = :userId AND r.burrowId = :burrowId")
	void remove(Long userId, Long burrowId);

	boolean existsByUserIdAndBurrowId(Long userId, Long burrowId);

	long countByBurrowId(Long burrowId);

	@Query("""
			SELECT r.burrowId AS burrowId, b.userId AS ownerId, r.createdAt AS createdAt
			FROM RegularBurrow r JOIN Burrow b ON b.id = r.burrowId JOIN User u ON u.id = b.userId
			WHERE r.userId = :userId AND u.status = com.itting.user.domain.UserStatus.ACTIVE
			ORDER BY r.createdAt DESC, r.burrowId DESC
			""")
	List<SavedBurrow> findSavedBurrows(Long userId);

	interface SavedBurrow {
		Long getBurrowId();

		Long getOwnerId();

		Instant getCreatedAt();
	}

}
