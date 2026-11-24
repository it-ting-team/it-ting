package com.itting.burrow.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.itting.burrow.domain.DailyRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DailyRecommendationRepository extends JpaRepository<DailyRecommendation, Long> {

	Optional<DailyRecommendation> findByUserIdAndRecommendedOn(Long userId, LocalDate recommendedOn);

	/** 사용자와 날짜가 같은 요청은 먼저 삽입한 트랜잭션의 결과를 기다린다. */
	@Modifying
	@Query(value = """
			INSERT IGNORE INTO daily_recommendation (user_id, recommended_on, created_at)
			VALUES (:userId, :recommendedOn, :now)
			""", nativeQuery = true)
	int insertIfAbsent(Long userId, LocalDate recommendedOn, Instant now);

	@Query(value = """
			SELECT b.user_id
			FROM burrow_entrance e
			JOIN burrow b ON b.id = e.burrow_id
			JOIN `user` u ON u.id = b.user_id
			JOIN user_profile p ON p.user_id = u.id
			LEFT JOIN (
			    SELECT m.target_user_id, COUNT(*) AS times_received
			    FROM daily_recommendation d
			    JOIN daily_recommendation_member m ON m.daily_recommendation_id = d.id
			    WHERE d.user_id = :userId
			    GROUP BY m.target_user_id
			) history ON history.target_user_id = u.id
			WHERE e.term_id = :termId AND e.campus_floor_id = :campusFloorId
			  AND p.generation = :generation AND u.status = 'ACTIVE' AND u.onboarded_at IS NOT NULL
			  AND u.id <> :userId
			  AND NOT EXISTS (
			    SELECT 1 FROM regular_burrow r WHERE r.user_id = :userId AND r.burrow_id = b.id
			  )
			ORDER BY COALESCE(history.times_received, 0), RAND()
			LIMIT 3
			""", nativeQuery = true)
	List<Long> selectCandidates(Long userId, Long termId, Long campusFloorId, int generation);

}
