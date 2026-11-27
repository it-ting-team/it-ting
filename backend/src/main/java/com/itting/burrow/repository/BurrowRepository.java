package com.itting.burrow.repository;

import java.util.Optional;

import com.itting.burrow.domain.Burrow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BurrowRepository extends JpaRepository<Burrow, Long> {

	Optional<Burrow> findByUserId(Long userId);

	@Query("SELECT b.userId FROM Burrow b WHERE b.id = :burrowId")
	Optional<Long> findOwnerId(Long burrowId);

	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("SELECT b FROM Burrow b WHERE b.id = :burrowId")
	Optional<Burrow> findByIdForShare(Long burrowId);

	/** 이 사람의 땅굴을 지운다(회원 탈퇴). 입구 · 단골 · 받은 방명록 글을 먼저 지운다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM Burrow b WHERE b.id = :burrowId")
	int deleteNow(@Param("burrowId") Long burrowId);

}
