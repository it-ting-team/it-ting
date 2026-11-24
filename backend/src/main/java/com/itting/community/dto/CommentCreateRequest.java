package com.itting.community.dto;

import com.itting.community.domain.CommentRules;
import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/posts/{postId}/comments}의 본문.
 *
 * @param replyToCommentId 답글이면 답하는 대상의 id. 댓글에 답하면 그 댓글의 id, 답글에 다시 답하면 그 답글의 id다. 댓글이면 null
 */
public record CommentCreateRequest(
		@Schema(description = "본문. 1 ~ 300자", example = "감사합니다. 신청할게요.")
		@NotBlank(message = "공백만으로는 쓸 수 없습니다.")
		@CodePointLength(max = CommentRules.CONTENT_MAX, message = "300자 이하로 써야 합니다.") String content,
		@Schema(description = "답글이면 답하는 대상의 id. 댓글을 쓸 때는 null", example = "903") Long replyToCommentId) {
}
