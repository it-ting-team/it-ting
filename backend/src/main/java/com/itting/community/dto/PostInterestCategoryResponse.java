package com.itting.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 글에 붙인 관심사 대분류, 많이 이야기하는 관심사 대분류.
 */
public record PostInterestCategoryResponse(
		@Schema(description = "대분류의 id. 글 목록의 interestCategoryId에 보낸다", example = "3") Long id,
		@Schema(description = "대분류 이름", example = "문화생활") String name) {
}
