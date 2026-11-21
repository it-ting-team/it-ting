package com.itting.campus.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/campuses}의 {@code data}.
 */
public record CampusListResponse(
		@Schema(description = "캠퍼스 목록. 표시 순서대로") List<CampusResponse> items) {

	public record CampusResponse(
			@Schema(description = "캠퍼스의 id", example = "1") Long id,
			@Schema(description = "캠퍼스 이름", example = "서울") String name,
			@Schema(description = "그 캠퍼스의 층 목록. 층 번호 순. 층이 없으면 빈 목록") List<FloorResponse> floors) {
	}

	public record FloorResponse(
			@Schema(description = "캠퍼스-층의 id. 다른 API의 campusFloorId에는 이 값을 보낸다", example = "12") Long id,
			@Schema(description = "층 번호", example = "2") int floor) {
	}

}
