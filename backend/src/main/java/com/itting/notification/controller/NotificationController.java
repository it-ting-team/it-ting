package com.itting.notification.controller;

import com.itting.global.response.ApiResult;
import com.itting.notification.domain.NotificationCategory;
import com.itting.notification.dto.NotificationListResponse;
import com.itting.notification.dto.NotificationPopupResponse;
import com.itting.notification.dto.NotificationUnreadCountResponse;
import com.itting.notification.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "09 알림", description = "알림 팝업, 알림 목록, 안 읽은 수, 읽음")
@RestController
@RequestMapping("/api/v1")
public class NotificationController {

	private final NotificationQueryService notificationQueryService;

	public NotificationController(NotificationQueryService notificationQueryService) {
		this.notificationQueryService = notificationQueryService;
	}

	@Operation(summary = "알림 팝업",
			description = "요청 두 줄(모임 참여 요청 수, 채팅 요청 수와 가장 최근 요청자)과 최근 알림 5개, 안 읽은 수를 준다. "
					+ "최근 알림은 요청 알림을 빼고 안 읽은 것 먼저 준다. 로그인이 필요하다.")
	@GetMapping("/me/notifications/popup")
	public ApiResult<NotificationPopupResponse> popup(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(notificationQueryService.popup(userId));
	}

	@Operation(summary = "알림 목록",
			description = "30일 안의 알림을 안 읽은 것 먼저, 각각 새것 먼저 커서 방식으로 준다. 요청 알림도 준다. "
					+ "이어 받는 사이에 알림을 읽거나 새 알림이 생겨도 이미 받은 알림이 다시 오지 않는다. 로그인이 필요하다.")
	@GetMapping("/me/notifications")
	public ApiResult<NotificationListResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Parameter(description = "갈래. 보내지 않으면 전체") @RequestParam(required = false) NotificationCategory category,
			@Parameter(description = "한 번에 받을 개수. 1 ~ 50") @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
			@Parameter(description = "앞 응답의 nextCursor. 처음에는 보내지 않는다") @RequestParam(required = false) String cursor) {
		return ApiResult.ok(notificationQueryService.list(userId, category, size, cursor));
	}

	@Operation(summary = "안 읽은 알림 수",
			description = "안 읽은 알림 수(모든 갈래)와 GNB 빨간 점을 켤지(지금 켜 둔 갈래만)를 준다. 로그인이 필요하다.")
	@GetMapping("/me/notifications/unread-count")
	public ApiResult<NotificationUnreadCountResponse> unreadCount(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(notificationQueryService.unreadCount(userId));
	}

	@Operation(summary = "알림 하나 읽음",
			description = "내가 받은 알림 하나를 읽음으로 바꾸고, 바꾼 뒤의 안 읽은 수를 준다. 이미 읽은 알림이면 그대로 성공한다. "
					+ "없거나 남의 알림이거나 30일이 지났으면 404 NOTIFICATION_NOT_FOUND. 로그인이 필요하다.")
	@PostMapping("/notifications/{notificationId}/read")
	public ApiResult<NotificationUnreadCountResponse> read(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Parameter(description = "알림의 id") @PathVariable Long notificationId) {
		return ApiResult.ok(notificationQueryService.read(userId, notificationId));
	}

	@Operation(summary = "모두 읽음",
			description = "고른 탭과 상관없이 내 알림을 모두 읽음으로 바꾼다. 요청 두 줄의 수는 줄지 않는다. 로그인이 필요하다.")
	@PostMapping("/me/notifications/read-all")
	public ApiResult<NotificationUnreadCountResponse> readAll(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(notificationQueryService.readAll(userId));
	}

}
