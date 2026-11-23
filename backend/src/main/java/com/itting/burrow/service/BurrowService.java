package com.itting.burrow.service;

import com.itting.burrow.domain.Burrow;
import com.itting.burrow.domain.BurrowEntrance;
import com.itting.burrow.repository.BurrowEntranceRepository;
import com.itting.burrow.repository.BurrowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 땅굴과 입구를 만들고 옮긴다. 온보딩 확정과 내 정보 수정이 부른다.
 */
@Service
public class BurrowService {

	private final BurrowRepository burrowRepository;

	private final BurrowEntranceRepository burrowEntranceRepository;

	public BurrowService(BurrowRepository burrowRepository, BurrowEntranceRepository burrowEntranceRepository) {
		this.burrowRepository = burrowRepository;
		this.burrowEntranceRepository = burrowEntranceRepository;
	}

	/**
	 * 땅굴을 만들고 입구를 그 들판의 다음 자리에 놓는다. 부른 쪽의 트랜잭션 안에서만 쓴다.
	 *
	 * <p>자리 번호는 캠퍼스-층 행을 잠근 뒤 정하므로, 같은 층에 동시에 놓아도 번호가 겹치지 않는다.
	 * 빈 자리(이사로 비운 번호)는 다시 쓰지 않는다.
	 *
	 * @return 만든 땅굴의 id
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Long createBurrow(Long userId, Long termId, Long campusFloorId) {
		Burrow burrow = burrowRepository.save(Burrow.create(userId));
		int slot = nextSlot(termId, campusFloorId);
		burrowEntranceRepository.save(BurrowEntrance.create(burrow.getId(), termId, campusFloorId, slot));
		return burrow.getId();
	}

	/**
	 * 땅굴의 입구를 그 학기 · 캠퍼스-층 들판의 다음 자리로 옮긴다. 있던 자리는 비고 다시 쓰지 않는다. 부른 쪽의 트랜잭션 안에서만 쓴다.
	 *
	 * <p>이미 그 들판에 있으면 옮기지 않는다. 자리 번호는 {@link #createBurrow}와 같은 잠금으로 정한다.
	 *
	 * @return 옮긴 뒤(또는 그대로인) 입구
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public BurrowEntrance moveEntrance(Long burrowId, Long termId, Long campusFloorId) {
		BurrowEntrance entrance = burrowEntranceRepository.findByBurrowId(burrowId)
				.orElseThrow(() -> new IllegalStateException("입구가 없는 땅굴: " + burrowId));
		if (entrance.getTermId().equals(termId) && entrance.getCampusFloorId().equals(campusFloorId)) {
			return entrance;
		}
		entrance.moveTo(termId, campusFloorId, nextSlot(termId, campusFloorId));
		return entrance;
	}

	/** 캠퍼스-층 행을 잠그고 그 들판의 가장 큰 자리 번호 + 1을 준다. */
	private int nextSlot(Long termId, Long campusFloorId) {
		if (burrowEntranceRepository.lockCampusFloor(campusFloorId) == null) {
			throw new IllegalStateException("없는 캠퍼스-층: " + campusFloorId);
		}
		return burrowEntranceRepository.findMaxSlotForUpdate(termId, campusFloorId) + 1;
	}

}
