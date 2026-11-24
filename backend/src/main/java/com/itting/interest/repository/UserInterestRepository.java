package com.itting.interest.repository;

import java.util.Collection;
import java.util.List;

import com.itting.interest.domain.UserInterest;
import com.itting.interest.dto.InterestTagResponse;
import org.springframework.data.jpa.repository.JpaRepository;
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

	/** 한 사람의 관심사 태그를 고른 순서로. {@code isShared}는 모두 false이고, 부른 쪽이 보는 사람 기준으로 다시 정한다. */
	@Query("""
			SELECT new com.itting.interest.dto.InterestTagResponse(i.id, i.name, i.interestCategoryId, false)
			FROM UserInterest ui
			JOIN Interest i ON i.id = ui.interestId
			WHERE ui.userId = :userId
			ORDER BY ui.sortOrder
			""")
	List<InterestTagResponse> findTagsByUserId(@Param("userId") Long userId);

	/** 한 사람이 고른 세부 관심사의 id. 순서는 없다. */
	@Query("SELECT ui.interestId FROM UserInterest ui WHERE ui.userId = :userId")
	List<Long> findInterestIdsByUserId(@Param("userId") Long userId);

	/**
	 * 여러 사람이 고른 관심사 가운데 보는 사람도 고른 것(겹치는 것)만. 사람마다 그 사람이 고른 순서({@code sort_order})로 준다.
	 * 겹치는 것이 없는 사람은 결과에 없다.
	 */
	@Query("""
			SELECT new com.itting.interest.repository.UserSharedInterest(ui.userId, i.id, i.name, i.interestCategoryId)
			FROM UserInterest ui
			JOIN Interest i ON i.id = ui.interestId
			WHERE ui.userId IN :ownerIds
			  AND ui.interestId IN (SELECT mine.interestId FROM UserInterest mine WHERE mine.userId = :viewerId)
			ORDER BY ui.userId, ui.sortOrder
			""")
	List<UserSharedInterest> findSharedInterests(@Param("ownerIds") Collection<Long> ownerIds,
			@Param("viewerId") Long viewerId);

}
