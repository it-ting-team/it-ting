package com.itting.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 글을 저장하거나 저장을 취소한 뒤의 값({@code PUT · DELETE /api/v1/posts/{postId}/bookmark}).
 */
public record PostBookmarkResponse(
		@Schema(description = "저장하면 항상 true, 취소하면 항상 false", example = "true") boolean isBookmarked) {
}
