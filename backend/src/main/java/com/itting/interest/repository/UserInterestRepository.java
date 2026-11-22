package com.itting.interest.repository;

import java.util.List;

import com.itting.interest.domain.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserInterestRepository extends JpaRepository<UserInterest, UserInterest.Key> {

	/** 관심사마다 고른 사람 수. 아무도 고르지 않은 관심사는 결과에 없다. */
	@Query("""
			SELECT new com.itting.interest.repository.InterestUserCount(ui.interestId, COUNT(ui))
			FROM UserInterest ui
			GROUP BY ui.interestId
			""")
	List<InterestUserCount> countUsersByInterest();

}
