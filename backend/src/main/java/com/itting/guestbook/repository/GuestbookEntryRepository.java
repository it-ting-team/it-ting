package com.itting.guestbook.repository;

import com.itting.guestbook.domain.GuestbookEntry;
import com.itting.guestbook.domain.GuestbookEntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestbookEntryRepository extends JpaRepository<GuestbookEntry, Long> {

	/** 땅굴에 달린 글 가운데 이 상태인 것의 수. 방명록 글 수는 {@code PUBLIC}으로 센다. */
	long countByBurrowIdAndStatus(Long burrowId, GuestbookEntryStatus status);

	/** 땅굴에 달린 글 가운데 이 상태인 것을 한 페이지. 순서는 {@code pageable}의 정렬을 따른다. */
	Page<GuestbookEntry> findByBurrowIdAndStatus(Long burrowId, GuestbookEntryStatus status, Pageable pageable);

}
