package com.itting.guestbook.service;

import com.itting.guestbook.repository.GuestbookEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴의 방명록 영역 정리({@code docs/db/burrow.md}의 "탈퇴자가 받은 방명록은 지우고 쓴 방명록은 남기기"). 탈퇴 트랜잭션
 * 안에서 땅굴을 지우기 전에 부른다.
 */
@Component
public class GuestbookWithdrawalCleaner {

	private final GuestbookEntryRepository entries;

	public GuestbookWithdrawalCleaner(GuestbookEntryRepository entries) {
		this.entries = entries;
	}

	/** 이 땅굴이 받은 방명록 글을 지운다. 탈퇴한 사람이 남의 땅굴에 쓴 글은 남긴다. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void cleanReceived(Long burrowId) {
		entries.deleteAllByBurrowId(burrowId);
	}

}
