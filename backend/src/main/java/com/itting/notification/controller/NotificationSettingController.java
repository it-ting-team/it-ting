package com.itting.notification.controller;

import com.itting.global.response.ApiResult;
import com.itting.notification.dto.NotificationSettingResponse;
import com.itting.notification.dto.NotificationSettingUpdateRequest;
import com.itting.notification.dto.NotificationSettingUpdateResponse;
import com.itting.notification.service.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10 설정", description = "계정 정보, 비밀번호 변경, 알림 설정, 공지사항")
@RestController
@RequestMapping("/api/v1/me/notification-settings")
public class NotificationSettingController {

	private final NotificationSettingService notificationSettingService;

	public NotificationSettingController(NotificationSettingService notificationSettingService) {
		this.notificationSettingService = notificationSettingService;
	}

	@Operation(summary = "알림 설정",
			description = "모임, 채팅, 커뮤니티, 땅굴, 공지 알림을 켰는지와 브라우저 푸시를 구독할 때 쓰는 공개 키를 준다. "
					+ "설정 줄이 없는 계정은 다섯 값을 모두 true로 준다. 공개 키는 아직 정하지 않았으면 null이다. "
					+ "\"브라우저 알림 받기\" 스위치의 상태는 주지 않는다. 로그인이 필요하다.")
	@GetMapping
	public ApiResult<NotificationSettingResponse> getSettings(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(notificationSettingService.getSettings(userId));
	}

	@Operation(summary = "알림 설정 바꾸기",
			description = "알림을 갈래별로 켜고 끈다. 다섯 값의 키를 모두 보낸다. 바꾸지 않은 갈래도 지금 값을 담는다. 키가 빠지면 400이다. "
					+ "이미 받은 알림은 지우지 않는다. 바꾸어 빨간 점이 달라지면 실시간 소식을 보낸다. 로그인이 필요하다.")
	@PatchMapping
	public ApiResult<NotificationSettingUpdateResponse> updateSettings(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody NotificationSettingUpdateRequest body) {
		return ApiResult.ok(notificationSettingService.updateSettings(userId, body));
	}

}
