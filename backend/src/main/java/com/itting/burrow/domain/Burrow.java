package com.itting.burrow.domain;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사람의 땅굴({@code burrow}). 온보딩을 확정할 때 만들고, 한 계정에 하나다.
 * 방 안의 모습은 저장하지 않고 주인의 관심사로 그린다. 들판에서의 자리는 {@link BurrowEntrance}에 있다.
 */
@Entity
@Table(name = "burrow")
public class Burrow extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	protected Burrow() {
	}

	public static Burrow create(Long userId) {
		Burrow burrow = new Burrow();
		burrow.userId = userId;
		return burrow;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

}
