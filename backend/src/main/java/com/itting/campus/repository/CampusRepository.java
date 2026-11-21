package com.itting.campus.repository;

import java.util.List;

import com.itting.campus.domain.Campus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusRepository extends JpaRepository<Campus, Long> {

	/** 지우지 않은 캠퍼스를 표시 순서대로. 순서가 같으면 id 순이다. */
	List<Campus> findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc();

}
