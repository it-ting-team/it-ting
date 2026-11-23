package com.itting.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/posts/{postId}/views}의 {@code data}.
 */
public record PostViewResponse(@Schema(description = "올린 뒤의 조회수", example = "88") int viewCount) {
}
