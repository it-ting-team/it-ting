package com.itting.community.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 글의 댓글 목록({@code GET /api/v1/posts/{postId}/comments}). 커서 방식이다.
 */
public record CommentListResponse(
		@Schema(description = "댓글(답글이 아닌 것)을 등록한 순서로. 답글은 댓글마다 붙는다") List<CommentThreadResponse> items,
		@Schema(description = "다음 댓글을 받을 때 보낼 값. 더 없으면 null") String nextCursor,
		@Schema(description = "더 받을 댓글이 있는지", example = "false") boolean hasNext) {
}
