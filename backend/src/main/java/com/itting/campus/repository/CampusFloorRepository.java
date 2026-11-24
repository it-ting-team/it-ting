package com.itting.campus.repository;

import java.util.List;
import java.util.Optional;

import com.itting.campus.domain.CampusFloor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusFloorRepository extends JpaRepository<CampusFloor, Long> {

	/** 지우지 않은 층을 층 번호 순으로. */
	List<CampusFloor> findAllByDeletedAtIsNullOrderByFloorAsc();

	@Query("""
			SELECT new com.itting.campus.repository.CampusFloorLocation(c.name, f.floor)
			FROM CampusFloor f JOIN Campus c ON c.id = f.campusId
			WHERE f.id = :id AND f.deletedAt IS NULL AND c.deletedAt IS NULL
			""")
	Optional<CampusFloorLocation> findAvailableLocation(@Param("id") Long id);

}
