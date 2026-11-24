package com.itting.community.controller;

import com.itting.community.domain.CommentRules;
import com.itting.community.dto.CommentCreateRequest;
import com.itting.community.dto.CommentCreateResponse;
import com.itting.community.dto.CommentDeleteResponse;
import com.itting.community.dto.CommentLikeResponse;
import com.itting.community.dto.CommentListResponse;
import com.itting.community.dto.CommentUpdateRequest;
import com.itting.community.dto.CommentUpdateResponse;
import com.itting.community.service.CommentQueryService;
import com.itting.community.service.CommentService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 댓글과 답글({@code docs/api/08-community.md}). 모두 로그인이 필요하고, 온보딩과 이번 학기 고르기를 마친 계정만 부른다.
 */
@Tag(name = "08 커뮤니티", description = "글 · 댓글 · 좋아요 · 저장")
@RestController
@RequestMapping("/api/v1")
public class CommentController {

	private final CommentService commentService;

	private final CommentQueryService commentQueryService;

	public CommentController(CommentService commentService, CommentQueryService commentQueryService) {
		this.commentService = commentService;
		this.commentQueryService = commentQueryService;
	}

	@Operation(summary = "댓글 목록",
			description = "글의 댓글(답글이 아닌 것)을 등록한 순서로 size개씩 주고, 댓글마다 답글을 모두 붙여 준다. 커서 방식이다. "
					+ "삭제된 댓글은 빼고, 가려진 댓글은 content가 null인 채로 준다. 가려진 글은 작성자만 볼 수 있다. 로그인이 필요하다.")
	@GetMapping("/posts/{postId}/comments")
	public ApiResult<CommentListResponse> getComments(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long postId,
			@Parameter(description = "한 번에 받을 댓글 수. 답글은 세지 않는다. 1 ~ 100")
			@RequestParam(defaultValue = "" + CommentRules.LIST_DEFAULT_SIZE)
			@Min(value = 1, message = "1 이상이어야 합니다.")
			@Max(value = CommentRules.LIST_MAX_SIZE, message = "100 이하여야 합니다.") int size,
			@Parameter(description = "앞 응답의 nextCursor. 처음에는 보내지 않는다")
			@RequestParam(required = false) String cursor) {
		return ApiResult.ok(commentQueryService.getComments(userId, postId, size, cursor));
	}

	@Operation(summary = "댓글 · 답글 쓰기",
			description = "댓글이나 답글을 쓴다. 답글이면 replyToCommentId에 답하는 대상의 id를 보낸다. 답글은 한 단계만 두므로 "
					+ "답글에 다시 답해도 같은 댓글 아래에 달리고 mentionUser로 누구에게 답했는지 남는다. "
					+ "댓글이면 글 작성자에게, 답글이면 답한 대상의 작성자에게 알림을 만든다. 가려지거나 삭제된 글에는 쓸 수 없다. "
					+ "로그인이 필요하다.")
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping("/posts/{postId}/comments")
	public ApiResult<CommentCreateResponse> create(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long postId,
			@Valid @RequestBody CommentCreateRequest body) {
		return ApiResult.ok(commentService.create(userId, postId, body));
	}

	@Operation(summary = "댓글 수정",
			description = "댓글이나 답글을 고친다. 쓴 사람만 할 수 있다. 본문이 달라졌을 때만 새 버전을 만든다. "
					+ "가려진 댓글과 가려진 글의 댓글은 고칠 수 없다. 로그인이 필요하다.")
	@PatchMapping("/comments/{commentId}")
	public ApiResult<CommentUpdateResponse> update(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long commentId,
			@Valid @RequestBody CommentUpdateRequest body) {
		return ApiResult.ok(commentService.update(userId, commentId, body));
	}

	@Operation(summary = "댓글 삭제",
			description = "댓글이나 답글을 지운다. 쓴 사람만 할 수 있다. 댓글을 지우면 달린 답글도 함께 지워진다. "
					+ "가려진 댓글과 가려진 글의 댓글도 지울 수 있다. 로그인이 필요하다.")
	@DeleteMapping("/comments/{commentId}")
	public ApiResult<CommentDeleteResponse> delete(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long commentId) {
		return ApiResult.ok(commentService.delete(userId, commentId));
	}

	@Operation(summary = "댓글 좋아요",
			description = "댓글이나 답글에 좋아요를 누른다. 이미 눌렀어도 성공이다. 새로 눌렀고 내 댓글이 아니면 댓글 작성자에게 "
					+ "알림을 만든다(취소하고 다시 눌러도 알림은 한 번이다). 삭제되거나 가려진 댓글과 가려지거나 삭제된 글의 댓글에는 "
					+ "누를 수 없다. 로그인이 필요하다.")
	@PutMapping("/comments/{commentId}/like")
	public ApiResult<CommentLikeResponse> like(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long commentId) {
		return ApiResult.ok(commentService.like(userId, commentId));
	}

	@Operation(summary = "댓글 좋아요 취소",
			description = "댓글이나 답글의 좋아요를 취소한다. 누르지 않았어도 성공이고, 댓글의 상태를 보지 않는다. "
					+ "이미 만든 알림은 지우지 않는다. 로그인이 필요하다.")
	@DeleteMapping("/comments/{commentId}/like")
	public ApiResult<CommentLikeResponse> unlike(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long commentId) {
		return ApiResult.ok(commentService.unlike(userId, commentId));
	}

}
