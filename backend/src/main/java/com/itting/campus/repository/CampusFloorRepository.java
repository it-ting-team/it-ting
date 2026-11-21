package com.itting.campus.repository;

import java.util.List;

import com.itting.campus.domain.CampusFloor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusFloorRepository extends JpaRepository<CampusFloor, Long> {

	/** 지우지 않은 층을 층 번호 순으로. */
	List<CampusFloor> findAllByDeletedAtIsNullOrderByFloorAsc();

}
