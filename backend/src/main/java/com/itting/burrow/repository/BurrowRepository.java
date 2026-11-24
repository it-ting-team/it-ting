package com.itting.burrow.repository;

import java.util.Optional;

import com.itting.burrow.domain.Burrow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BurrowRepository extends JpaRepository<Burrow, Long> {

	Optional<Burrow> findByUserId(Long userId);

	@Query("SELECT b.userId FROM Burrow b WHERE b.id = :burrowId")
	Optional<Long> findOwnerId(Long burrowId);

	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("SELECT b FROM Burrow b WHERE b.id = :burrowId")
	Optional<Burrow> findByIdForShare(Long burrowId);

}
