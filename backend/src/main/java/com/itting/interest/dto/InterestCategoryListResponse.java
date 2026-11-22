package com.itting.interest.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/interest-categories}의 {@code data}.
 */
public record InterestCategoryListResponse(
		@Schema(description = "대분류 목록. id 순") List<InterestCategoryResponse> items) {

	public record InterestCategoryResponse(
			@Schema(description = "대분류의 id", example = "1") Long id,
			@Schema(description = "대분류 이름", example = "음식·음료") String name,
			@Schema(description = "그 대분류의 세부 관심사 목록. id 순") List<InterestResponse> interests) {
	}

	public record InterestResponse(
			@Schema(description = "세부 관심사의 id. 관심사를 저장하는 API의 interestIds에는 이 값을 보낸다", example = "2") Long id,
			@Schema(description = "세부 관심사 이름", example = "카페/커피") String name,
			@Schema(description = "그 관심사를 고른 사람 수. 아무도 고르지 않았으면 0", example = "57") long userCount) {
	}

}
