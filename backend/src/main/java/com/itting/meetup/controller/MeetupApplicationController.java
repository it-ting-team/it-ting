package com.itting.meetup.controller;

import com.itting.global.response.ApiResult;
import com.itting.meetup.dto.MeetupApplicationListResponse;
import com.itting.meetup.dto.MeetupApplicationRequest;
import com.itting.meetup.dto.MeetupApplicationResponse;
import com.itting.meetup.service.MeetupApplicationService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "07 모임")
@RestController
@RequestMapping("/api/v1")
public class MeetupApplicationController {

	private final MeetupApplicationService applications;

	public MeetupApplicationController(MeetupApplicationService applications) {
		this.applications = applications;
	}

	@Operation(summary = "모임 참여 신청")
	@PostMapping("/meetups/{meetupId}/applications")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResult<MeetupApplicationResponse> apply(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody(required = false) MeetupApplicationRequest request) {
		return ApiResult.ok(applications.apply(meetupId, userId, request == null ? null : request.message()));
	}

	@Operation(summary = "모임 신청 목록")
	@GetMapping("/meetups/{meetupId}/applications")
	public ApiResult<MeetupApplicationListResponse> list(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(applications.list(meetupId, userId));
	}

	@Operation(summary = "대기 중인 참여 신청 철회")
	@PostMapping("/meetups/{meetupId}/withdraw-application")
	public ApiResult<Void> withdraw(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		applications.withdraw(meetupId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "확정된 내 모임 참여 취소")
	@PostMapping("/meetups/{meetupId}/cancel-participation")
	public ApiResult<Void> cancelParticipation(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		applications.cancelParticipation(meetupId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "모임 신청 수락")
	@PostMapping("/meetup-applications/{meetupApplicationId}/accept")
	public ApiResult<Void> accept(@PathVariable Long meetupApplicationId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		applications.accept(meetupApplicationId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "모임 신청 거절")
	@PostMapping("/meetup-applications/{meetupApplicationId}/reject")
	public ApiResult<Void> reject(@PathVariable Long meetupApplicationId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		applications.reject(meetupApplicationId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "모임 확정자 내보내기")
	@PostMapping("/meetup-applications/{meetupApplicationId}/kick")
	public ApiResult<Void> kick(@PathVariable Long meetupApplicationId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		applications.kick(meetupApplicationId, userId);
		return ApiResult.ok();
	}

}
