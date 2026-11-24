package com.itting.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 댓글을 고친 뒤의 값({@code PATCH /api/v1/comments/{commentId}}).
 */
public record CommentUpdateResponse(
		@Schema(description = "고친 뒤의 본문", example = "감사합니다. 방금 신청했어요.") String content,
		@Schema(description = "고친 적이 있는지. 본문이 그대로면 바뀌지 않는다", example = "true") boolean isEdited) {
}
