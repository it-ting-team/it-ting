package com.itting.community.dto;

import java.util.List;

import com.itting.community.domain.PostCategory;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/posts/highlights}의 {@code data}.
 */
public record PostHighlightsResponse(
		@Schema(description = "이번 주 인기 글. 5개까지") List<WeeklyTopPost> weeklyTopPosts,
		@Schema(description = "많이 이야기하는 관심사 대분류. 8개까지") List<PostInterestCategoryResponse> trendingInterestCategories) {

	public record WeeklyTopPost(
			@Schema(description = "순위. 1부터", example = "1") int rank,
			@Schema(description = "글의 id", example = "204") Long id,
			@Schema(description = "PROMOTION, REVIEW, FREE 가운데 하나", example = "FREE") PostCategory category,
			@Schema(description = "제목", example = "5층 라운지 커피머신 쓰는 법") String title) {
	}

}
