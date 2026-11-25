package com.itting.community.dto;

import java.time.Instant;
import java.util.List;

import com.itting.community.domain.PostCategory;
import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 활동 목록의 글 한 줄. 글 카드({@link PostCardResponse})의 값에 내 활동의 값을 더한 것이다.
 */
public record MyPostCardResponse(
		@Schema(description = "글의 id", example = "204") Long id,
		@Schema(description = "PROMOTION, REVIEW, FREE 가운데 하나", example = "PROMOTION") PostCategory category,
		@Schema(description = "제목", example = "토요일 아침 한강 러닝 같이 하실 분") String title,
		@Schema(description = "본문의 앞 100자", example = "여의나루역에서 만나 5km를 천천히 뜁니다.") String contentPreview,
		@Schema(description = "첫 사진의 주소. 사진이 없으면 null") String thumbnailUrl,
		@Schema(description = "작성자") UserDisplayResponse author,
		@Schema(description = "글에 붙인 관심사 대분류. 없으면 []") List<PostInterestCategoryResponse> interestCategories,
		@Schema(description = "쓴 시각", example = "2026-11-19T02:30:00Z") Instant createdAt,
		@Schema(description = "조회수", example = "88") int viewCount,
		@Schema(description = "좋아요 수", example = "13") long likeCount,
		@Schema(description = "댓글 수. 댓글과 답글을 더한다", example = "6") long commentCount,
		@Schema(description = "내가 저장했는지", example = "false") boolean isBookmarked,
		@Schema(description = "신고로 가려진 글인지. 내가 쓴 글에서만 true가 온다", example = "false") boolean isHidden,
		@Schema(description = "내가 신고한 글인지", example = "false") boolean isReportedByMe,
		@Schema(description = "지금 이 글을 고칠 수 있는지. 내 글이고 가려지지 않았으면 true", example = "false") boolean canEdit,
		@Schema(description = "내가 저장한 시각. tab이 BOOKMARKED일 때만 주고 그 밖에는 null") Instant bookmarkedAt,
		@Schema(description = "그 글에 내가 단 가장 최근의 댓글이나 답글. tab이 COMMENTED일 때만 주고 그 밖에는 null")
		MyLatestComment myLatestComment,
		@Schema(description = "그 글에 내가 단 댓글과 답글의 수. tab이 COMMENTED가 아니면 0", example = "2") long myCommentCount) {

	/**
	 * @param id 그 댓글의 id. 글 상세에서 이 댓글의 자리로 간다
	 * @param content 그 댓글의 지금 본문
	 */
	public record MyLatestComment(
			@Schema(description = "댓글의 id", example = "907") Long id,
			@Schema(description = "댓글의 본문", example = "감사합니다. 방금 신청했어요.") String content) {
	}

	/** 글 카드에 내 활동의 값을 더한다. */
	public static MyPostCardResponse of(PostCardResponse card, boolean canEdit, Instant bookmarkedAt,
			MyLatestComment myLatestComment, long myCommentCount) {
		return new MyPostCardResponse(card.id(), card.category(), card.title(), card.contentPreview(),
				card.thumbnailUrl(), card.author(), card.interestCategories(), card.createdAt(), card.viewCount(),
				card.likeCount(), card.commentCount(), card.isBookmarked(), card.isHidden(), card.isReportedByMe(),
				canEdit, bookmarkedAt, myLatestComment, myCommentCount);
	}

}
