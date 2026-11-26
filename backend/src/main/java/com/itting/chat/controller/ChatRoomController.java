package com.itting.chat.controller;

import com.itting.chat.domain.ChatRules;
import com.itting.chat.dto.ChatMessageListResponse;
import com.itting.chat.dto.ChatMessageResponse;
import com.itting.chat.dto.ChatMessageSendRequest;
import com.itting.chat.dto.ChatNoticeRequest;
import com.itting.chat.dto.ChatNoticeResponse;
import com.itting.chat.dto.ChatReadRequest;
import com.itting.chat.dto.ChatReadResponse;
import com.itting.chat.dto.ChatRoomDetailResponse;
import com.itting.chat.dto.ChatRoomListResponse;
import com.itting.chat.service.ChatMessageService;
import com.itting.chat.service.ChatRoomQueryService;
import com.itting.chat.service.ChatRoomService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대화방 API 8개({@code docs/api/06-chat.md}의 대화 목록, 대화방 조회, 메시지 조회 · 보내기, 읽음, 나가기, 공지 쓰기 · 지우기).
 * 모두 로그인이 필요하고, 온보딩과 이번 학기 고르기를 마친 계정만 부른다. 들어갈 수 없는 대화방은 없는 대화방과 똑같이
 * {@code 404 CHAT_ROOM_NOT_FOUND}로 답한다.
 */
@Tag(name = "06 채팅", description = "대화 요청 · 대화방 · 메시지 · 읽음 · 나가기 · 공지")
@RestController
@RequestMapping("/api/v1")
public class ChatRoomController {

	private final ChatRoomQueryService chatRoomQueryService;

	private final ChatMessageService chatMessageService;

	private final ChatRoomService chatRoomService;

	public ChatRoomController(ChatRoomQueryService chatRoomQueryService, ChatMessageService chatMessageService,
			ChatRoomService chatRoomService) {
		this.chatRoomQueryService = chatRoomQueryService;
		this.chatMessageService = chatMessageService;
		this.chatRoomService = chatRoomService;
	}

	@Operation(summary = "대화 목록",
			description = "내가 들어가 있는 1:1 · 모임 대화방을 마지막 메시지가 새로운 것부터 한 번에 모두 준다. 방마다 마지막 메시지와 "
					+ "안 읽은 수를 싣는다. 내가 나간 대화방과 삭제된 모임의 대화방은 주지 않는다. 로그인이 필요하다.")
	@GetMapping("/me/chat-rooms")
	public ApiResult<ChatRoomListResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(chatRoomQueryService.list(userId));
	}

	@Operation(summary = "대화방 조회",
			description = "대화방 하나를 열 때 필요한 값을 준다. 1:1은 상대와 겹치는 관심사, 모임은 모임 정보 · 공지 · 지금 있는 사람이다. "
					+ "leaveType이 나가기 버튼이 지금 하는 일을 알려 준다. 메시지와 안 읽은 수는 담지 않는다. 로그인이 필요하다.")
	@GetMapping("/chat-rooms/{chatRoomId}")
	public ApiResult<ChatRoomDetailResponse> detail(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long chatRoomId) {
		return ApiResult.ok(chatRoomQueryService.detail(userId, chatRoomId));
	}

	@Operation(summary = "메시지 조회",
			description = "대화방의 메시지를 새 것부터 커서 방식으로 나눠 준다. 사람이 보낸 메시지와 안내 메시지를 함께 주고, 내가 들어오기 "
					+ "전의 메시지도 준다. 조회만으로는 읽은 자리가 바뀌지 않는다. 로그인이 필요하다.")
	@GetMapping("/chat-rooms/{chatRoomId}/messages")
	public ApiResult<ChatMessageListResponse> messages(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long chatRoomId,
			@Parameter(description = "한 번에 받을 개수. 1 ~ 100") @RequestParam(defaultValue = "" + ChatRules.PAGE_DEFAULT)
			@Min(1) @Max(ChatRules.PAGE_MAX) int size,
			@Parameter(description = "앞 응답의 nextCursor. 처음에는 보내지 않는다") @RequestParam(required = false) String cursor) {
		return ApiResult.ok(chatRoomQueryService.messages(userId, chatRoomId, size, cursor));
	}

	@Operation(summary = "메시지 보내기",
			description = "대화방에 메시지를 보낸다. 글만, 사진만(사진 올리기로 받은 id, 3장까지), 또는 둘 다 보낸다. 대화방의 다른 "
					+ "사람에게 새 메시지 알림이 간다. 상대가 나갔거나 탈퇴한 1:1 대화방이면 409(CHAT_ROOM_READ_ONLY)다. "
					+ "로그인이 필요하다.")
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping("/chat-rooms/{chatRoomId}/messages")
	public ApiResult<ChatMessageResponse> send(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long chatRoomId, @Valid @RequestBody ChatMessageSendRequest body) {
		return ApiResult.ok(chatMessageService.send(userId, chatRoomId, body));
	}

	@Operation(summary = "읽음",
			description = "이 대화방을 어디까지 읽었는지 기록하고 그 뒤의 안 읽은 수를 준다. 읽은 자리는 뒤로만 옮겨진다. 그 대화방의 "
					+ "안 읽은 새 메시지 알림도 읽음으로 바뀐다. 상대에게는 알리지 않는다. 로그인이 필요하다.")
	@PostMapping("/chat-rooms/{chatRoomId}/read")
	public ApiResult<ChatReadResponse> read(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long chatRoomId, @Valid @RequestBody ChatReadRequest body) {
		return ApiResult.ok(chatMessageService.read(userId, chatRoomId, body.messageId()));
	}

	@Operation(summary = "대화방에서 나가기",
			description = "대화방에서만 나간다. 모임의 참여는 바꾸지 않는다. 1:1은 언제나, 모임은 취소됐거나 끝났을 때만 나갈 수 있고 "
					+ "그 밖에는 409(CHAT_ROOM_LEAVE_NOT_ALLOWED)다. 끝나지 않은 모임에서는 모임의 참여 취소를 부른다. "
					+ "한 번 나간 대화방에는 다시 들어갈 수 없다. 로그인이 필요하다.")
	@PostMapping("/chat-rooms/{chatRoomId}/leave")
	public ApiResult<Void> leave(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long chatRoomId) {
		chatRoomService.leave(userId, chatRoomId);
		return ApiResult.ok();
	}

	@Operation(summary = "공지 쓰기 · 고치기",
			description = "모임 대화방의 공지를 쓰거나 고친다. 공지는 대화방에 하나뿐이다. 주최자만 부를 수 있고, 주최자가 아니거나 "
					+ "1:1 대화방이면 403(FORBIDDEN)이다. 다른 참여자에게 실시간으로 알리지 않는다. 로그인이 필요하다.")
	@PutMapping("/chat-rooms/{chatRoomId}/notice")
	public ApiResult<ChatNoticeResponse> writeNotice(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long chatRoomId, @Valid @RequestBody ChatNoticeRequest body) {
		return ApiResult.ok(chatRoomService.writeNotice(userId, chatRoomId, body.content()));
	}

	@Operation(summary = "공지 지우기",
			description = "모임 대화방의 공지를 지운다. 공지가 없었어도 성공이다. 주최자만 부를 수 있다. 로그인이 필요하다.")
	@DeleteMapping("/chat-rooms/{chatRoomId}/notice")
	public ApiResult<Void> deleteNotice(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long chatRoomId) {
		chatRoomService.deleteNotice(userId, chatRoomId);
		return ApiResult.ok();
	}

}
