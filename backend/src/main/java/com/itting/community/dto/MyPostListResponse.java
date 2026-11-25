package com.itting.community.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/me/posts}의 {@code data}. 페이지 방식이다({@code docs/api-convention.md}).
 */
public record MyPostListResponse(
		@Schema(description = "그 탭의 글") List<MyPostCardResponse> items,
		@Schema(description = "지금 페이지. 1부터", example = "1") int page,
		@Schema(description = "한 페이지의 글 수", example = "20") int size,
		@Schema(description = "조건에 맞는 글의 수", example = "1") long totalItems,
		@Schema(description = "전체 페이지 수", example = "1") long totalPages) {
}
