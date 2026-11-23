package com.itting.community.dto;

import java.time.Instant;
import java.util.List;

import com.itting.community.domain.PostCategory;
import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 글 카드({@code docs/api/08-community.md}의 글 카드). 글을 목록으로 보여 주는 곳이 함께 쓴다.
 */
public record PostCardResponse(
		@Schema(description = "글의 id", example = "204") Long id,
		@Schema(description = "PROMOTION, REVIEW, FREE 가운데 하나", example = "FREE") PostCategory category,
		@Schema(description = "제목", example = "5층 라운지 커피머신 쓰는 법") String title,
		@Schema(description = "본문의 앞 100자", example = "물통을 먼저 채우고 전원을 켜면 됩니다.") String contentPreview,
		@Schema(description = "첫 사진의 주소. 사진이 없으면 null") String thumbnailUrl,
		@Schema(description = "작성자") UserDisplayResponse author,
		@Schema(description = "글에 붙인 관심사 대분류. 없으면 []") List<PostInterestCategoryResponse> interestCategories,
		@Schema(description = "쓴 시각", example = "2026-11-19T02:30:00Z") Instant createdAt,
		@Schema(description = "조회수", example = "87") int viewCount,
		@Schema(description = "좋아요 수", example = "12") long likeCount,
		@Schema(description = "댓글 수. 댓글과 답글을 더한다", example = "5") long commentCount,
		@Schema(description = "내가 저장했는지", example = "false") boolean isBookmarked,
		@Schema(description = "신고로 가려진 글인지. 내가 쓴 글 목록에서만 true가 온다", example = "false") boolean isHidden,
		@Schema(description = "내가 신고한 글인지", example = "false") boolean isReportedByMe) {
}
