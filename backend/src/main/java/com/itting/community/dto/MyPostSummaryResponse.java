package com.itting.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/me/posts/summary}의 {@code data}. 카테고리로 거르지 않은 전체의 수다.
 */
public record MyPostSummaryResponse(
		@Schema(description = "내가 쓴 글의 수. 가려진 글도 센다", example = "4") long writtenCount,
		@Schema(description = "댓글 단 글의 수", example = "11") long commentedCount,
		@Schema(description = "저장한 글의 수", example = "7") long bookmarkedCount) {
}
