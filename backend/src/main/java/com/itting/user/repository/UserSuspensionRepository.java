package com.itting.user.repository;

import java.time.Instant;
import java.util.List;

import com.itting.user.domain.UserSuspension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSuspensionRepository extends JpaRepository<UserSuspension, Long> {

	/** {@code now}에 정지 중인 기록. 가장 늦게 끝나는 것부터 준다. */
	@Query("""
			select s from UserSuspension s
			where s.userId = :userId
			  and s.startsAt <= :now
			  and s.endsAt > :now
			  and s.liftedAt is null
			order by s.endsAt desc
			""")
	List<UserSuspension> findActive(@Param("userId") Long userId, @Param("now") Instant now);

}
