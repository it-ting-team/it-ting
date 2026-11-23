package com.itting.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/posts}의 {@code data}.
 */
public record PostCreateResponse(@Schema(description = "만든 글의 id", example = "204") Long postId) {
}
