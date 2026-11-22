package com.itting.term.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/terms}의 {@code data}.
 */
public record TermListResponse(
		@Schema(description = "열린 학기 목록. 최근에 열린 것부터라 첫 번째가 이번 학기다") List<TermResponse> items) {

	public record TermResponse(
			@Schema(description = "학기의 id", example = "1") Long id,
			@Schema(description = "연도", example = "2026") int year,
			@Schema(description = "1은 상반기, 2는 하반기", example = "2") int half,
			@Schema(description = "이번 학기인지. 하나만 true다", example = "true") boolean isCurrent) {
	}

}
