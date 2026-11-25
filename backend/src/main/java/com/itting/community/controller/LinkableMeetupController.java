package com.itting.community.controller;

import com.itting.community.domain.PostCategory;
import com.itting.community.dto.LinkableMeetupsResponse;
import com.itting.community.service.PostQueryService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글에 연결할 수 있는 내 모임({@code docs/api/08-community.md}). 로그인이 필요하고, 온보딩과 이번 학기 고르기를 마친 계정만 부른다.
 */
@Tag(name = "08 커뮤니티", description = "글 · 댓글 · 좋아요 · 저장")
@RestController
@RequestMapping("/api/v1/me/linkable-meetups")
public class LinkableMeetupController {

	private final PostQueryService postQueryService;

	public LinkableMeetupController(PostQueryService postQueryService) {
		this.postQueryService = postQueryService;
	}

	@Operation(summary = "글에 연결할 수 있는 내 모임",
			description = "글을 쓰거나 고칠 때 모임 연결에 고를 수 있는 모임의 카드를 나누지 않고 모두 준다. "
					+ "PROMOTION은 내가 연 모집 중인 모임(시작이 가까운 순), REVIEW는 내가 열었거나 참여가 확정된 지난 모임(시작이 최근인 순), "
					+ "FREE는 내가 열었거나 참여가 확정된 모임(지난 모임 포함, 시작이 최근인 순)이다. "
					+ "취소 · 삭제된 모임은 주지 않는다. 로그인이 필요하다.")
	@GetMapping
	public ApiResult<LinkableMeetupsResponse> getLinkableMeetups(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Parameter(description = "쓰려는 글의 카테고리. PROMOTION, REVIEW, FREE", required = true)
			@RequestParam PostCategory postCategory) {
		return ApiResult.ok(postQueryService.getLinkableMeetups(userId, postCategory));
	}

}
