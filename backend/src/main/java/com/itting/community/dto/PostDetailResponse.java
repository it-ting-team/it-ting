package com.itting.community.dto;

import java.time.Instant;
import java.util.List;

import com.itting.community.domain.PostCategory;
import com.itting.meetup.dto.MeetupListResponse.MeetupCardResponse;
import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/posts/{postId}}의 {@code data}.
 *
 * @param meetup 글에 연결한 모임의 카드. 연결하지 않았거나 모임이 삭제됐으면 null이다. 모임이 취소되거나 모집 완료가 되면
 *        글은 그대로이고 카드의 {@code recruitStatus}만 바뀐다
 */
public record PostDetailResponse(
		@Schema(description = "글의 id", example = "204") Long id,
		@Schema(description = "PROMOTION, REVIEW, FREE 가운데 하나", example = "FREE") PostCategory category,
		@Schema(description = "제목", example = "5층 라운지 커피머신 쓰는 법") String title,
		@Schema(description = "본문", example = "물통을 먼저 채우고 전원을 켜면 됩니다.") String content,
		@Schema(description = "사진. 보여 주는 순서대로이고 첫 장이 대표 사진이다. 없으면 []") List<Image> images,
		@Schema(description = "작성자") UserDisplayResponse author,
		@Schema(description = "작성자의 관심사 가운데 나와 겹치는 것. 내 글이면 []") List<SharedInterest> authorSharedInterests,
		@Schema(description = "글에 붙인 관심사 대분류. 없으면 []") List<PostInterestCategoryResponse> interestCategories,
		@Schema(description = "글에 연결한 모임의 카드. 연결하지 않았거나 모임이 삭제됐으면 null. 모임이 취소되거나 모집 완료가 되면 "
				+ "카드의 recruitStatus가 바뀐다", nullable = true) MeetupCardResponse meetup,
		@Schema(description = "쓴 시각", example = "2026-11-19T02:30:00Z") Instant createdAt,
		@Schema(description = "제목이나 본문을 고친 적이 있는지", example = "false") boolean isEdited,
		@Schema(description = "조회수", example = "87") int viewCount,
		@Schema(description = "좋아요 수", example = "12") long likeCount,
		@Schema(description = "댓글 수. 댓글과 답글을 더한다", example = "5") long commentCount,
		@Schema(description = "내가 좋아요를 눌렀는지", example = "false") boolean isLiked,
		@Schema(description = "내가 저장했는지", example = "false") boolean isBookmarked,
		@Schema(description = "내가 쓴 글인지", example = "false") boolean isMine,
		@Schema(description = "신고로 가려진 글인지. 작성자에게만 true가 온다", example = "false") boolean isHidden,
		@Schema(description = "내가 신고한 글인지", example = "false") boolean isReportedByMe,
		@Schema(description = "지금 이 글을 고칠 수 있는지. 내 글이고 가려지지 않았으면 true", example = "false") boolean canEdit) {

	public record Image(
			@Schema(description = "사진의 id. 글을 고칠 때 imageIds에 보낸다", example = "512") Long id,
			@Schema(description = "사진의 주소") String url) {
	}

	/** 관심사 태그({@code docs/api/00-common.md}). 나와 겹치는 것만 담으므로 {@code isShared}는 늘 true다. */
	public record SharedInterest(
			@Schema(description = "세부 관심사의 id", example = "12") Long id,
			@Schema(description = "세부 관심사 이름", example = "러닝/마라톤") String name,
			@Schema(description = "속한 대분류의 id", example = "2") Long interestCategoryId,
			@Schema(description = "나와 겹치는 관심사인지", example = "true") boolean isShared) {
	}

}
