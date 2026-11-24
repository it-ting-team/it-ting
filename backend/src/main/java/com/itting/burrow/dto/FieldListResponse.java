package com.itting.burrow.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record FieldListResponse(List<FieldResponse> items) {

	public record FieldResponse(Long campusFloorId,
			@Schema(description = "그 학기와 층에 입구가 하나도 없는지") boolean isEmpty) {
	}

}
