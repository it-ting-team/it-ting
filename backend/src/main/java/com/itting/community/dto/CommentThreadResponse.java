package com.itting.community.dto;

import java.time.Instant;
import java.util.List;

import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 댓글 목록의 한 줄. {@link CommentResponse}의 값에 그 댓글에 달린 답글을 더한 것이다.
 */
public record CommentThreadResponse(
		@Schema(description = "댓글의 id", example = "901") Long id,
		@Schema(description = "쓴 사람") UserDisplayResponse author,
		@Schema(description = "본문. 가려진 댓글은 null", example = "몇 시에 끝나나요?") String content,
		@Schema(description = "댓글에는 늘 null") CommentResponse.MentionUser mentionUser,
		@Schema(description = "쓴 시각", example = "2026-11-19T03:02:00Z") Instant createdAt,
		@Schema(description = "고친 적이 있는지", example = "false") boolean isEdited,
		@Schema(description = "좋아요 수", example = "2") long likeCount,
		@Schema(description = "내가 좋아요를 눌렀는지", example = "false") boolean isLiked,
		@Schema(description = "내가 쓴 댓글인지", example = "false") boolean isMine,
		@Schema(description = "신고로 가려진 댓글인지", example = "false") boolean isHidden,
		@Schema(description = "내가 신고한 댓글인지", example = "false") boolean isReportedByMe,
		@Schema(description = "그 댓글에 달린 답글을 등록한 순서로. 없으면 []") List<CommentResponse> replies) {

	public static CommentThreadResponse of(CommentResponse comment, List<CommentResponse> replies) {
		return new CommentThreadResponse(comment.id(), comment.author(), comment.content(), comment.mentionUser(),
				comment.createdAt(), comment.isEdited(), comment.likeCount(), comment.isLiked(), comment.isMine(),
				comment.isHidden(), comment.isReportedByMe(), replies);
	}

}
