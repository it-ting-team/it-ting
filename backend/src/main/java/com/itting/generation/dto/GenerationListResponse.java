package com.itting.generation.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/generations}의 {@code data}.
 */
public record GenerationListResponse(
		@Schema(description = "기수 목록. 큰 기수부터") List<GenerationResponse> items) {

	public record GenerationResponse(@Schema(description = "기수", example = "15") int generation) {
	}

}
