package com.itting.community.dto;

import java.time.Instant;

import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 댓글과 답글이 함께 쓰는 모양({@code docs/api/08-community.md}의 댓글의 모양). 댓글 목록의 답글이 이 모양이다.
 */
public record CommentResponse(
		@Schema(description = "댓글의 id", example = "903") Long id,
		@Schema(description = "쓴 사람") UserDisplayResponse author,
		@Schema(description = "본문. 가려진 댓글은 null", example = "9시쯤 끝나요!") String content,
		@Schema(description = "답글에 다시 답했을 때 \"@닉네임\"으로 보여 줄 사람. 없으면 null") MentionUser mentionUser,
		@Schema(description = "쓴 시각", example = "2026-11-19T03:10:00Z") Instant createdAt,
		@Schema(description = "고친 적이 있는지", example = "false") boolean isEdited,
		@Schema(description = "좋아요 수", example = "1") long likeCount,
		@Schema(description = "내가 좋아요를 눌렀는지", example = "true") boolean isLiked,
		@Schema(description = "내가 쓴 댓글인지", example = "false") boolean isMine,
		@Schema(description = "신고로 가려진 댓글인지", example = "false") boolean isHidden,
		@Schema(description = "내가 신고한 댓글인지", example = "false") boolean isReportedByMe) {

	/**
	 * 답글에 다시 답했을 때 "@닉네임"으로 보여 줄 사람.
	 */
	public record MentionUser(
			@Schema(description = "그 사람의 계정 id", example = "31") Long id,
			@Schema(description = "그 사람의 닉네임. 탈퇴했으면 null", example = "두더지왕") String nickname) {
	}

}
