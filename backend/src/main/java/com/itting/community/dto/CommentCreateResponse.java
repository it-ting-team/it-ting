package com.itting.community.dto;

import java.time.Instant;

import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 댓글이나 답글을 쓴 뒤의 값({@code POST /api/v1/posts/{postId}/comments}). {@link CommentResponse}의 값에
 * 답글이 달린 댓글의 id와 쓴 뒤의 댓글 수를 더한 것이다.
 */
public record CommentCreateResponse(
		@Schema(description = "만든 댓글의 id", example = "907") Long id,
		@Schema(description = "쓴 사람") UserDisplayResponse author,
		@Schema(description = "본문", example = "감사합니다. 신청할게요.") String content,
		@Schema(description = "답글에 다시 답했을 때 \"@닉네임\"으로 보여 줄 사람. 없으면 null")
		CommentResponse.MentionUser mentionUser,
		@Schema(description = "쓴 시각", example = "2026-11-19T03:15:00Z") Instant createdAt,
		@Schema(description = "고친 적이 있는지. 새로 쓴 댓글은 false", example = "false") boolean isEdited,
		@Schema(description = "좋아요 수. 새로 쓴 댓글은 0", example = "0") long likeCount,
		@Schema(description = "내가 좋아요를 눌렀는지. 새로 쓴 댓글은 false", example = "false") boolean isLiked,
		@Schema(description = "내가 쓴 댓글인지. 늘 true", example = "true") boolean isMine,
		@Schema(description = "신고로 가려진 댓글인지. 새로 쓴 댓글은 false", example = "false") boolean isHidden,
		@Schema(description = "내가 신고한 댓글인지. 새로 쓴 댓글은 false", example = "false") boolean isReportedByMe,
		@Schema(description = "답글이면 그 답글이 달린 댓글의 id. 댓글이면 null", example = "901") Long parentCommentId,
		@Schema(description = "쓴 뒤의 그 글의 댓글 수", example = "6") long commentCount) {

	public static CommentCreateResponse of(CommentResponse comment, Long parentCommentId, long commentCount) {
		return new CommentCreateResponse(comment.id(), comment.author(), comment.content(), comment.mentionUser(),
				comment.createdAt(), comment.isEdited(), comment.likeCount(), comment.isLiked(), comment.isMine(),
				comment.isHidden(), comment.isReportedByMe(), parentCommentId, commentCount);
	}

}
