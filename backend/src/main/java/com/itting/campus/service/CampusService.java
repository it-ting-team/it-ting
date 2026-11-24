package com.itting.campus.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.itting.campus.domain.Campus;
import com.itting.campus.domain.CampusErrorCode;
import com.itting.campus.domain.CampusFloor;
import com.itting.campus.dto.CampusListResponse;
import com.itting.campus.dto.CampusListResponse.CampusResponse;
import com.itting.campus.dto.CampusListResponse.FloorResponse;
import com.itting.campus.repository.CampusFloorRepository;
import com.itting.campus.repository.CampusFloorLocation;
import com.itting.campus.repository.CampusRepository;
import com.itting.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CampusService {

	private final CampusRepository campusRepository;

	private final CampusFloorRepository campusFloorRepository;

	public CampusService(CampusRepository campusRepository, CampusFloorRepository campusFloorRepository) {
		this.campusRepository = campusRepository;
		this.campusFloorRepository = campusFloorRepository;
	}

	/**
	 * 지우지 않은 캠퍼스와 층을 모두 준다. 캠퍼스는 표시 순서, 층은 층 번호 순이다.
	 * 목록이 작아(캠퍼스 몇 개, 층 수십 개) 나누지 않고 두 번의 조회로 모두 읽는다.
	 */
	public CampusListResponse getCampuses() {
		List<Campus> campuses = campusRepository.findAllByDeletedAtIsNullOrderBySortOrderAscIdAsc();
		Map<Long, List<FloorResponse>> floorsByCampusId = campusFloorRepository
				.findAllByDeletedAtIsNullOrderByFloorAsc()
				.stream()
				.collect(Collectors.groupingBy(CampusFloor::getCampusId,
						Collectors.mapping(floor -> new FloorResponse(floor.getId(), floor.getFloor()),
								Collectors.toList())));

		List<CampusResponse> items = campuses.stream()
				.map(campus -> new CampusResponse(campus.getId(), campus.getName(),
						floorsByCampusId.getOrDefault(campus.getId(), List.of())))
				.toList();
		return new CampusListResponse(items);
	}

	/** 캠퍼스 선택 목록에 나오는 층의 이름과 번호다. */
	public CampusFloorLocation getAvailableFloor(Long campusFloorId) {
		return campusFloorRepository.findAvailableLocation(campusFloorId)
				.orElseThrow(() -> new BusinessException(CampusErrorCode.CAMPUS_FLOOR_NOT_FOUND));
	}

}
