package com.itting.burrow.domain;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 땅굴이 지금 놓인 들판과 자리({@code burrow_entrance}). 들판은 학기와 캠퍼스-층의 짝이다.
 * 땅굴 하나에 한 행이고, 새 학기 이사나 캠퍼스-층 변경 때는 이 행의 학기 · 층 · 자리를 바꾼다.
 */
@Entity
@Table(name = "burrow_entrance")
public class BurrowEntrance extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "burrow_id", nullable = false, updatable = false)
	private Long burrowId;

	@Column(name = "term_id", nullable = false)
	private Long termId;

	@Column(name = "campus_floor_id", nullable = false)
	private Long campusFloorId;

	/** 그 들판에서의 자리 번호. 1부터 놓인 순서대로다. */
	@Column(nullable = false)
	private int slot;

	protected BurrowEntrance() {
	}

	public static BurrowEntrance create(Long burrowId, Long termId, Long campusFloorId, int slot) {
		BurrowEntrance entrance = new BurrowEntrance();
		entrance.burrowId = burrowId;
		entrance.termId = termId;
		entrance.campusFloorId = campusFloorId;
		entrance.slot = slot;
		return entrance;
	}

	public Long getId() {
		return id;
	}

	public Long getBurrowId() {
		return burrowId;
	}

	public Long getTermId() {
		return termId;
	}

	public Long getCampusFloorId() {
		return campusFloorId;
	}

	public int getSlot() {
		return slot;
	}

}
