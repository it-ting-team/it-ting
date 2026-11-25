package com.itting.community.controller;

import com.itting.community.domain.MyPostTab;
import com.itting.community.domain.PostCategory;
import com.itting.community.dto.MyPostListResponse;
import com.itting.community.dto.MyPostSummaryResponse;
import com.itting.community.service.MyPostQueryService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 커뮤니티의 내 활동({@code docs/api/08-community.md}). 모두 로그인이 필요하고, 온보딩과 이번 학기 고르기를 마친 계정만 부른다.
 */
@Tag(name = "08 커뮤니티", description = "글 · 댓글 · 좋아요 · 저장")
@RestController
@RequestMapping("/api/v1/me/posts")
public class MyPostController {

	private final MyPostQueryService myPostQueryService;

	public MyPostController(MyPostQueryService myPostQueryService) {
		this.myPostQueryService = myPostQueryService;
	}

	@Operation(summary = "내 활동의 글 목록",
			description = "내가 쓴 글(WRITTEN, 가려진 글 포함, 새 글부터), 댓글 단 글(COMMENTED, 최근에 댓글 단 글부터), "
					+ "저장한 글(BOOKMARKED, 최근에 저장한 글부터) 가운데 하나를 페이지로 준다. 삭제된 글은 없고, "
					+ "댓글 단 글과 저장한 글에는 가려진 글도 없다. 로그인이 필요하다.")
	@GetMapping
	public ApiResult<MyPostListResponse> getMyPosts(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Parameter(description = "WRITTEN, COMMENTED, BOOKMARKED 가운데 하나")
			@RequestParam MyPostTab tab,
			@Parameter(description = "PROMOTION, REVIEW, FREE. 보내지 않으면 전체")
			@RequestParam(required = false) PostCategory category,
			@Parameter(description = "페이지 번호. 1부터")
			@RequestParam(defaultValue = "1") @Min(value = 1, message = "1 이상이어야 합니다.") int page,
			@Parameter(description = "한 페이지의 글 수. 1 ~ 50")
			@RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 50, message = "50 이하여야 합니다.") int size) {
		return ApiResult.ok(myPostQueryService.getMyPosts(userId, tab, category, page, size));
	}

	@Operation(summary = "내 활동의 개수",
			description = "내가 쓴 글, 댓글 단 글, 저장한 글의 수를 준다. 카테고리로 거르지 않은 전체의 수다. 로그인이 필요하다.")
	@GetMapping("/summary")
	public ApiResult<MyPostSummaryResponse> getSummary(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(myPostQueryService.getSummary(userId));
	}

}
