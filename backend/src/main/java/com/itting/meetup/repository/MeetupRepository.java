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

}
