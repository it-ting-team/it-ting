package com.itting.chat.controller;

import com.itting.chat.domain.ChatRequestDirection;
import com.itting.chat.dto.ChatRequestAcceptResponse;
import com.itting.chat.dto.ChatRequestCountResponse;
import com.itting.chat.dto.ChatRequestCreateRequest;
import com.itting.chat.dto.ChatRequestCreateResponse;
import com.itting.chat.dto.ChatRequestListResponse;
import com.itting.chat.service.ChatRequestQueryService;
import com.itting.chat.service.ChatRequestService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대화 요청 API 6개({@code docs/api/05-explore-visit.md}의 보내기, {@code docs/api/06-chat.md}의 목록 · 수 · 수락 · 거절 ·
 * 취소). 모두 로그인이 필요하고, 온보딩과 이번 학기 고르기를 마친 계정만 부른다.
 */
@Tag(name = "06 채팅", description = "대화 요청 · 대화방 · 메시지 · 읽음 · 나가기 · 공지")
@RestController
@RequestMapping("/api/v1")
public class ChatRequestController {

	private final ChatRequestService chatRequestService;

	private final ChatRequestQueryService chatRequestQueryService;

	public ChatRequestController(ChatRequestService chatRequestService,
			ChatRequestQueryService chatRequestQueryService) {
		this.chatRequestService = chatRequestService;
		this.chatRequestQueryService = chatRequestQueryService;
	}

	@Operation(summary = "대화 요청 보내기",
			description = "다른 사람에게 1:1 대화 요청을 보낸다. 받은 사람에게 대화 요청 알림이 간다. "
					+ "내가 거절한 요청이 남아 있는 사람에게 보내면 요청을 만들지 않고 그 요청을 내가 수락한 것으로 처리해 대화방을 바로 열고"
					+ "(응답의 chatRoomId), 인사는 내가 보낸 첫 메시지가 된다. 이미 대화 중이거나 살아 있는 요청이 있으면 보낼 수 없다. "
					+ "로그인이 필요하다.")
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping("/chat-requests")
	public ApiResult<ChatRequestCreateResponse> send(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody ChatRequestCreateRequest body) {
		return ApiResult.ok(chatRequestService.send(userId, body));
	}

	@Operation(summary = "대화 요청 목록",
			description = "받은 요청(RECEIVED)이나 보낸 요청(SENT)을 새로 보낸 것부터 한 번에 모두 준다. 살아 있는 요청만 주고, "
					+ "받은 요청에는 내가 거절한 요청이 없다. 보낸 요청에는 거절됐는지 알려 주는 값이 없다. 로그인이 필요하다.")
	@GetMapping("/me/chat-requests")
	public ApiResult<ChatRequestListResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Parameter(description = "RECEIVED(받은 요청), SENT(보낸 요청)", required = true)
			@RequestParam ChatRequestDirection direction) {
		return ApiResult.ok(chatRequestQueryService.list(userId, direction));
	}

	@Operation(summary = "대화 요청 수",
			description = "받은 요청 수와 보낸 요청 수를 준다. 요청 목록의 개수와 늘 같다. 로그인이 필요하다.")
	@GetMapping("/me/chat-requests/count")
	public ApiResult<ChatRequestCountResponse> count(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(chatRequestQueryService.count(userId));
	}

	@Operation(summary = "대화 요청 수락",
			description = "받은 요청을 수락해 1:1 대화방을 만들고 id를 준다. 보낸 사람에게 수락 알림이 간다. 이미 수락한 요청이면 그때 만든 "
					+ "대화방의 id를 준다. 취소됐거나 만료됐거나 내가 이미 거절한 요청은 404다. 로그인이 필요하다.")
	@PostMapping("/chat-requests/{chatRequestId}/accept")
	public ApiResult<ChatRequestAcceptResponse> accept(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId, @PathVariable Long chatRequestId) {
		return ApiResult.ok(chatRequestService.accept(userId, chatRequestId));
	}

	@Operation(summary = "대화 요청 거절",
			description = "받은 요청을 거절한다. 보낸 사람에게는 알리지 않고, 보낸 사람에게는 만료될 때까지 그대로 보인다. "
					+ "이미 거절한 요청을 다시 거절해도 성공이다. 로그인이 필요하다.")
	@PostMapping("/chat-requests/{chatRequestId}/reject")
	public ApiResult<Void> reject(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long chatRequestId) {
		chatRequestService.reject(userId, chatRequestId);
		return ApiResult.ok();
	}

	@Operation(summary = "대화 요청 취소",
			description = "내가 보낸 요청을 취소한다. 받은 사람이 이미 거절한 요청도 똑같이 취소되고 응답도 같다. 상대가 그사이에 "
					+ "수락했으면 409(CHAT_REQUEST_ALREADY_ACCEPTED)와 detail.chatRoomId를 준다. 알림은 만들지 않는다. "
					+ "로그인이 필요하다.")
	@PostMapping("/chat-requests/{chatRequestId}/cancel")
	public ApiResult<Void> cancel(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long chatRequestId) {
		chatRequestService.cancel(userId, chatRequestId);
		return ApiResult.ok();
	}

}
