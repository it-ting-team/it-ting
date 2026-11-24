package com.itting.burrow.domain;

import java.io.Serializable;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "regular_burrow")
@IdClass(RegularBurrow.Key.class)
public class RegularBurrow extends BaseCreatedTimeEntity {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "burrow_id")
	private Long burrowId;

	protected RegularBurrow() {
	}

	public record Key(Long userId, Long burrowId) implements Serializable {
	}

}
