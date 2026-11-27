package com.itting.interest.repository;

import java.util.Collection;
import java.util.List;

import com.itting.interest.domain.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInterestRepository extends JpaRepository<UserInterest, UserInterest.Key> {

	/** 관심사마다 고른 사람 수. 아무도 고르지 않은 관심사는 결과에 없다. */
	@Query("""
			SELECT new com.itting.interest.repository.InterestUserCount(ui.interestId, COUNT(ui))
			FROM UserInterest ui
			GROUP BY ui.interestId
			""")
	List<InterestUserCount> countUsersByInterest();

	/** 한 사람이 고른 관심사를 고른 순서({@code sort_order})로. */
	List<UserInterest> findAllByUserIdOrderBySortOrderAsc(Long userId);

	/** 계정별 관심사 태그를 고른 순서로 읽는다. */
	@Query("""
			SELECT new com.itting.interest.repository.UserInterestTagRow(ui.userId, i.id, i.name, i.interestCategoryId)
			FROM UserInterest ui
			JOIN Interest i ON i.id = ui.interestId
			WHERE ui.userId IN :userIds
			ORDER BY ui.userId, ui.sortOrder
			""")
	List<UserInterestTagRow> findTagRowsByUserIds(@Param("userIds") Collection<Long> userIds);

	/** 한 사람이 고른 세부 관심사의 id. 순서는 없다. */
	@Query("SELECT ui.interestId FROM UserInterest ui WHERE ui.userId = :userId")
	List<Long> findInterestIdsByUserId(@Param("userId") Long userId);

	/** 이 사람의 관심사를 모두 지운다(회원 탈퇴). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM UserInterest i WHERE i.userId = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);

}
