package com.itting.burrow.service;

import com.itting.burrow.domain.Burrow;
import com.itting.burrow.repository.BurrowEntranceRepository;
import com.itting.burrow.repository.BurrowRepository;
import com.itting.burrow.repository.RegularBurrowRepository;
import com.itting.guestbook.service.GuestbookWithdrawalCleaner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴의 땅굴 영역 정리({@code docs/api/10-settings.md}의 "탈퇴할 때 하는 일"). 탈퇴 트랜잭션 안에서만 부른다.
 * 내가 담은 단골과 나를 담은 단골, 내 땅굴의 입구와 받은 방명록 글을 지운 뒤 땅굴을 지운다(FK 순서).
 */
@Component
public class BurrowWithdrawalCleaner {

	private final BurrowRepository burrows;

	private final BurrowEntranceRepository entrances;

	private final RegularBurrowRepository regulars;

	private final GuestbookWithdrawalCleaner guestbook;

	public BurrowWithdrawalCleaner(BurrowRepository burrows, BurrowEntranceRepository entrances,
			RegularBurrowRepository regulars, GuestbookWithdrawalCleaner guestbook) {
		this.burrows = burrows;
		this.entrances = entrances;
		this.regulars = regulars;
		this.guestbook = guestbook;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void clean(Long userId) {
		Long burrowId = burrows.findByUserId(userId).map(Burrow::getId).orElse(null);
		regulars.deleteAllByUserOrBurrow(userId, burrowId);
		if (burrowId == null) {
			// 온보딩을 마치지 않아 땅굴이 없는 계정이다.
			return;
		}
		entrances.deleteAllByBurrowId(burrowId);
		guestbook.cleanReceived(burrowId);
		burrows.deleteNow(burrowId);
	}

}
