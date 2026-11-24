package com.itting.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 글의 좋아요를 누르거나 취소한 뒤의 값({@code PUT · DELETE /api/v1/posts/{postId}/like}).
 */
public record PostLikeResponse(
		@Schema(description = "누르면 항상 true, 취소하면 항상 false", example = "true") boolean isLiked,
		@Schema(description = "누르거나 취소한 뒤의 좋아요 수", example = "13") long likeCount) {
}
