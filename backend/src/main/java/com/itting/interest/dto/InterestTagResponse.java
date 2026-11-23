package com.itting.interest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관심사 태그({@code docs/api/00-common.md}의 "관심사 태그"). 사람의 관심사를 보여 주는 곳에 쓴다.
 */
public record InterestTagResponse(
		@Schema(description = "세부 관심사의 id", example = "12") Long id,
		@Schema(description = "세부 관심사 이름", example = "러닝/마라톤") String name,
		@Schema(description = "속한 대분류의 id", example = "2") Long interestCategoryId,
		@Schema(description = "요청한 사람과 겹치는 관심사인지. 내 관심사를 볼 때는 false", example = "true") boolean isShared) {
}
