package com.itting.notification.controller;

import com.itting.global.response.ApiResult;
import com.itting.notification.dto.PushSubscriptionRequest;
import com.itting.notification.dto.PushSubscriptionResponse;
import com.itting.notification.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10 설정", description = "계정 정보, 비밀번호 변경, 알림 설정, 공지사항")
@RestController
@RequestMapping("/api/v1/me/push-subscriptions")
public class PushSubscriptionController {

	private final PushSubscriptionService pushSubscriptionService;

	public PushSubscriptionController(PushSubscriptionService pushSubscriptionService) {
		this.pushSubscriptionService = pushSubscriptionService;
	}

	@Operation(summary = "브라우저 푸시 등록",
			description = "이 브라우저를 브라우저 푸시를 받을 곳으로 등록한다. 브라우저가 만든 푸시 구독의 endpoint와 keys를 그대로 보낸다. "
					+ "이미 등록된 브라우저(같은 endpoint)면 새 줄을 만들지 않고 그 등록의 주인을 나로, 키를 보낸 값으로 바꾼다. "
					+ "로그인이 필요하다.")
	@ApiResponse(responseCode = "201", description = "새로 등록했다")
	@ApiResponse(responseCode = "200", description = "이미 등록돼 있던 브라우저다. 주인과 키를 바꿨다")
	@PostMapping
	public ResponseEntity<ApiResult<PushSubscriptionResponse>> register(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody PushSubscriptionRequest body) {
		PushSubscriptionService.Registration registration = pushSubscriptionService.register(userId, body);
		return ResponseEntity.status(registration.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ApiResult.ok(new PushSubscriptionResponse(registration.pushSubscriptionId())));
	}

	@Operation(summary = "브라우저 푸시 해제",
			description = "이 브라우저의 푸시 등록을 지운다. 없는 id이거나 남의 등록이면 아무것도 지우지 않고 성공으로 답한다. "
					+ "로그인이 필요하다.")
	@DeleteMapping("/{pushSubscriptionId}")
	public ApiResult<Void> unregister(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Parameter(description = "등록할 때 받은 pushSubscriptionId", example = "37")
			@PathVariable Long pushSubscriptionId) {
		pushSubscriptionService.unregister(userId, pushSubscriptionId);
		return ApiResult.ok();
	}

}
