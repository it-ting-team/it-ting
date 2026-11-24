package com.itting.burrow.repository;

import java.util.List;

import com.itting.burrow.domain.DailyRecommendationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DailyRecommendationMemberRepository
		extends JpaRepository<DailyRecommendationMember, DailyRecommendationMember.Key> {

	@Query("""
			SELECT b.id AS burrowId, m.targetUserId AS userId
			FROM DailyRecommendationMember m JOIN User u ON u.id = m.targetUserId
			JOIN Burrow b ON b.userId = u.id
			WHERE m.dailyRecommendationId = :recommendationId AND u.status = com.itting.user.domain.UserStatus.ACTIVE
			ORDER BY m.sortOrder
			""")
	List<Member> findVisibleMembers(Long recommendationId);

	interface Member {
		Long getBurrowId();

		Long getUserId();
	}

}
