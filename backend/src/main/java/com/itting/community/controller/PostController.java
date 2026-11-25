package com.itting.community.controller;

import com.itting.community.domain.PostCategory;
import com.itting.community.domain.PostSort;
import com.itting.community.dto.PostCreateRequest;
import com.itting.community.dto.PostCreateResponse;
import com.itting.community.dto.PostDetailResponse;
import com.itting.community.dto.PostHighlightsResponse;
import com.itting.community.dto.PostListResponse;
import com.itting.community.dto.PostUpdateRequest;
import com.itting.community.dto.PostViewResponse;
import com.itting.community.dto.RelatedPostsResponse;
import com.itting.community.service.PostQueryService;
import com.itting.community.service.PostService;
import com.itting.global.response.ApiResult;
import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 커뮤니티 글({@code docs/api/08-community.md}). 모두 로그인이 필요하고, 온보딩과 이번 학기 고르기를 마친 계정만 부른다.
 */
@Tag(name = "08 커뮤니티", description = "글 · 댓글 · 좋아요 · 저장")
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

	private final PostService postService;

	private final PostQueryService postQueryService;

	public PostController(PostService postService, PostQueryService postQueryService) {
		this.postService = postService;
		this.postQueryService = postQueryService;
	}

	@Operation(summary = "글 목록",
			description = "공개 글을 카테고리 · 내 관심사 · 관심사 대분류 · 검색어로 추리고 정렬해 페이지로 준다. "
					+ "검색어는 앞뒤 공백을 지우고 최신 제목 · 본문 · 작성자 닉네임에서 찾는다. 로그인이 필요하다.")
	@GetMapping
	public ApiResult<PostListResponse> getPosts(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Parameter(description = "PROMOTION, REVIEW, FREE. 보내지 않으면 전체")
			@RequestParam(required = false) PostCategory category,
			@Parameter(description = "true면 내 관심사가 속한 대분류의 태그가 달린 글만")
			@RequestParam(defaultValue = "false") boolean isMyInterestsOnly,
			@Parameter(description = "이 관심사 대분류의 태그가 달린 글만")
			@RequestParam(required = false) Long interestCategoryId,
			@Parameter(description = "검색어. 50자 이하")
			@RequestParam(required = false) @CodePointLength(max = 50, message = "50자 이하로 써야 합니다.") String keyword,
			@Parameter(description = "LATEST(최신순), POPULAR(인기순), MOST_COMMENTED(댓글순)")
			@RequestParam(defaultValue = "LATEST") PostSort sort,
			@Parameter(description = "페이지 번호. 1부터")
			@RequestParam(defaultValue = "1") @Min(value = 1, message = "1 이상이어야 합니다.") int page,
			@Parameter(description = "한 페이지의 글 수. 1 ~ 50")
			@RequestParam(defaultValue = "20") @Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = 50, message = "50 이하여야 합니다.") int size) {
		return ApiResult.ok(postQueryService.getPosts(userId, category, isMyInterestsOnly, interestCategoryId, keyword,
				sort, page, size));
	}

	@Operation(summary = "이번 주 인기 글과 많이 이야기하는 관심사",
			description = "최근 7일 좋아요 수가 많은 공개 글 5개(0점 제외)와, 최근 7일에 쓴 공개 글의 태그가 많은 대분류 8개를 준다. "
					+ "로그인이 필요하다.")
	@GetMapping("/highlights")
	public ApiResult<PostHighlightsResponse> getHighlights() {
		return ApiResult.ok(postQueryService.getHighlights());
	}

	@Operation(summary = "글쓰기",
			description = "글을 쓴다. 모임 홍보 · 모임 후기는 모임을 꼭 연결해야 하고(meetupId가 없으면 400), 연결할 모임은 "
					+ "카테고리마다 고를 수 있는 것이 다르다(GET /api/v1/me/linkable-meetups). 없거나 삭제된 모임이면 404 MEETUP_NOT_FOUND, "
					+ "그 카테고리에 연결할 수 없는 모임이면 409 MEETUP_NOT_LINKABLE이다. 로그인이 필요하다.")
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping
	public ApiResult<PostCreateResponse> create(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@RequestBody PostCreateRequest body) {
		return ApiResult.ok(postService.create(userId, body));
	}

	@Operation(summary = "글 상세",
			description = "글 하나의 본문과 그 글에서의 내 상태를 준다. 조회수는 올리지 않는다. "
					+ "가려진 글은 작성자에게만 준다. 로그인이 필요하다.")
	@GetMapping("/{postId}")
	public ApiResult<PostDetailResponse> getPost(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long postId) {
		return ApiResult.ok(postQueryService.getPost(userId, postId));
	}

	@Operation(summary = "글 수정",
			description = "글을 고친다. 바꾸지 않은 값도 모두 보낸다(meetupId는 null이라도 키를 보낸다). "
					+ "제목이나 본문이 달라졌을 때만 새 버전을 만든다. meetupId나 category를 바꿨을 때만 연결할 수 있는 모임인지 다시 "
					+ "검사한다(404 MEETUP_NOT_FOUND, 409 MEETUP_NOT_LINKABLE). 작성자만 할 수 있고 가려진 글은 고칠 수 없다. 로그인이 필요하다.")
	@PatchMapping("/{postId}")
	public ApiResult<Void> update(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long postId, @RequestBody PostUpdateRequest body) {
		postService.update(userId, postId, body);
		return ApiResult.ok();
	}

	@Operation(summary = "글 삭제",
			description = "글과 달린 댓글 · 답글을 지운다. 작성자만 할 수 있고 가려진 글도 지울 수 있다. 로그인이 필요하다.")
	@DeleteMapping("/{postId}")
	public ApiResult<Void> delete(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long postId) {
		postService.delete(userId, postId);
		return ApiResult.ok();
	}

	@Operation(summary = "조회수 올리기",
			description = "글의 조회수를 1 올린다. 글 상세를 열 때 함께 부른다. 작성자가 열어도 센다. 로그인이 필요하다.")
	@PostMapping("/{postId}/views")
	public ApiResult<PostViewResponse> increaseViewCount(@PathVariable Long postId) {
		return ApiResult.ok(postService.increaseViewCount(postId));
	}

	@Operation(summary = "다음 읽을 글",
			description = "지금 글의 태그와 대분류가 하나라도 같은 공개 글 가운데 최근 7일 좋아요가 많은 3개를 준다. "
					+ "태그가 없는 글이면 모든 공개 글에서 고른다. 로그인이 필요하다.")
	@GetMapping("/{postId}/related-posts")
	public ApiResult<RelatedPostsResponse> getRelatedPosts(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long postId) {
		return ApiResult.ok(postQueryService.getRelatedPosts(userId, postId));
	}

}
