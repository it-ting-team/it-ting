package com.itting.community.dto;

import com.itting.community.domain.CommentRules;
import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code PATCH /api/v1/comments/{commentId}}의 본문.
 */
public record CommentUpdateRequest(
		@Schema(description = "고친 본문. 1 ~ 300자", example = "감사합니다. 방금 신청했어요.")
		@NotBlank(message = "공백만으로는 쓸 수 없습니다.")
		@CodePointLength(max = CommentRules.CONTENT_MAX, message = "300자 이하로 써야 합니다.") String content) {
}
