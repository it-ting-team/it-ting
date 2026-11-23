package com.itting.community.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/posts/{postId}/related-posts}의 {@code data}.
 */
public record RelatedPostsResponse(@Schema(description = "글 카드의 목록. 3개까지") List<PostCardResponse> items) {
}
