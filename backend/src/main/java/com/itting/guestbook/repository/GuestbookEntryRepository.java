package com.itting.guestbook.repository;

import java.util.Optional;

import com.itting.guestbook.domain.GuestbookEntry;
import com.itting.guestbook.domain.GuestbookEntryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestbookEntryRepository extends JpaRepository<GuestbookEntry, Long> {

	/** 땅굴에 달린 글 가운데 이 상태인 것의 수. 방명록 글 수는 {@code PUBLIC}으로 센다. */
	long countByBurrowIdAndStatus(Long burrowId, GuestbookEntryStatus status);

	/** 땅굴에 달린 글 가운데 이 상태인 것을 한 페이지. 순서는 {@code pageable}의 정렬을 따른다. */
	Page<GuestbookEntry> findByBurrowIdAndStatus(Long burrowId, GuestbookEntryStatus status, Pageable pageable);

	/** 글을 트랜잭션이 끝날 때까지 잠근다. 신고를 받을 때 같은 글의 신고가 차례로 세어지게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT e FROM GuestbookEntry e WHERE e.id = :id")
	Optional<GuestbookEntry> findByIdForUpdate(@Param("id") Long id);

	/** 이 땅굴이 받은 방명록 글을 모두 지운다(회원 탈퇴). 탈퇴한 사람이 남의 땅굴에 쓴 글은 남긴다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM GuestbookEntry g WHERE g.burrowId = :burrowId")
	int deleteAllByBurrowId(@Param("burrowId") Long burrowId);

}
