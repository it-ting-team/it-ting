package com.itting.meetup.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.itting.meetup.domain.Meetup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MeetupRepository extends JpaRepository<Meetup, Long> {

	Optional<Meetup> findByIdAndDeletedAtIsNull(Long id);

	List<Meetup> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT m FROM Meetup m WHERE m.id = :id AND m.deletedAt IS NULL")
	Optional<Meetup> findVisibleForUpdate(Long id);

	@Query("""
			SELECT m.id FROM Meetup m
			WHERE m.deletedAt IS NULL AND m.canceledAt IS NULL AND m.details.startsAt > :now
			  AND m.details.startsAt BETWEEN :from AND :through
			""")
	List<Long> findReminderCandidates(Instant from, Instant through, Instant now);

	/**
	 * 내가 연 예정 모임. 시작하지 않았고 취소 · 삭제되지 않은 모임을 시작이 가까운 것부터 준다. 내 모임의 {@code tab=HOSTING}과
	 * 같다. 탈퇴를 막는 모임이다({@code docs/api/10-settings.md}의 "탈퇴할 때 하는 일").
	 */
	@Query("""
			SELECT m FROM Meetup m
			WHERE m.hostId = :hostId AND m.deletedAt IS NULL AND m.canceledAt IS NULL AND m.details.startsAt > :now
			ORDER BY m.details.startsAt, m.id
			""")
	List<Meetup> findHostingUpcoming(Long hostId, Instant now);

}
