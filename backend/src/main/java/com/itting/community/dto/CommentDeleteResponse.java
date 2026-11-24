package com.itting.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 댓글을 지운 뒤의 값({@code DELETE /api/v1/comments/{commentId}}).
 */
public record CommentDeleteResponse(
		@Schema(description = "지운 뒤의 그 글의 댓글 수. 댓글과 답글을 더하고 삭제된 것은 뺀다", example = "5") long commentCount) {
}
