package com.itting.community.controller;

import com.itting.community.dto.PostBookmarkResponse;
import com.itting.community.dto.PostLikeResponse;
import com.itting.community.service.PostReactionService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글의 좋아요와 저장({@code docs/api/08-community.md}). 모두 로그인이 필요하고, 온보딩과 이번 학기 고르기를 마친 계정만 부른다.
 */
@Tag(name = "08 커뮤니티", description = "글 · 댓글 · 좋아요 · 저장")
@RestController
@RequestMapping("/api/v1/posts/{postId}")
public class PostReactionController {

	private final PostReactionService postReactionService;

	public PostReactionController(PostReactionService postReactionService) {
		this.postReactionService = postReactionService;
	}

	@Operation(summary = "글 좋아요",
			description = "글에 좋아요를 누른다. 이미 눌렀어도 성공이다. 새로 눌렀고 내 글이 아니면 글 작성자에게 알림을 만든다"
					+ "(취소하고 다시 눌러도 알림은 한 번이다). 가려지거나 삭제된 글에는 누를 수 없다. 로그인이 필요하다.")
	@PutMapping("/like")
	public ApiResult<PostLikeResponse> like(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long postId) {
		return ApiResult.ok(postReactionService.like(userId, postId));
	}

	@Operation(summary = "글 좋아요 취소",
			description = "글의 좋아요를 취소한다. 누르지 않았어도 성공이고, 글의 상태를 보지 않는다. "
					+ "이미 만든 알림은 지우지 않는다. 로그인이 필요하다.")
	@DeleteMapping("/like")
	public ApiResult<PostLikeResponse> unlike(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long postId) {
		return ApiResult.ok(postReactionService.unlike(userId, postId));
	}

	@Operation(summary = "글 저장",
			description = "글을 저장한다. 이미 저장했어도 성공이고, 내 글도 저장할 수 있다. 알림은 만들지 않는다. "
					+ "가려지거나 삭제된 글은 저장할 수 없다. 로그인이 필요하다.")
	@PutMapping("/bookmark")
	public ApiResult<PostBookmarkResponse> bookmark(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long postId) {
		return ApiResult.ok(postReactionService.bookmark(userId, postId));
	}

	@Operation(summary = "글 저장 취소",
			description = "글의 저장을 취소한다. 저장하지 않았어도 성공이고, 글의 상태를 보지 않는다. 로그인이 필요하다.")
	@DeleteMapping("/bookmark")
	public ApiResult<PostBookmarkResponse> unbookmark(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long postId) {
		return ApiResult.ok(postReactionService.unbookmark(userId, postId));
	}

}
