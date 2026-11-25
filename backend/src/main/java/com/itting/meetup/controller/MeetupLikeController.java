package com.itting.meetup.controller;

import com.itting.global.response.ApiResult;
import com.itting.meetup.dto.MeetupLikeResponse;
import com.itting.meetup.dto.MeetupVacancyAlertResponse;
import com.itting.meetup.service.MeetupLikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "07 모임")
@RestController
@RequestMapping("/api/v1/meetups/{meetupId}")
public class MeetupLikeController {

	private final MeetupLikeService likes;

	public MeetupLikeController(MeetupLikeService likes) {
		this.likes = likes;
	}

	@Operation(summary = "모임 찜하기")
	@PutMapping("/like")
	public ApiResult<MeetupLikeResponse> like(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		likes.like(meetupId, userId);
		return ApiResult.ok(new MeetupLikeResponse(true));
	}

	@Operation(summary = "모임 찜 해제")
	@DeleteMapping("/like")
	public ApiResult<MeetupLikeResponse> unlike(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		likes.unlike(meetupId, userId);
		return ApiResult.ok(new MeetupLikeResponse(false));
	}

	@Operation(summary = "모임 빈자리 알림 켜기")
	@PutMapping("/vacancy-alert")
	public ApiResult<MeetupVacancyAlertResponse> enableAlert(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		likes.enableAlert(meetupId, userId);
		return ApiResult.ok(new MeetupVacancyAlertResponse(true));
	}

	@Operation(summary = "모임 빈자리 알림 끄기")
	@DeleteMapping("/vacancy-alert")
	public ApiResult<MeetupVacancyAlertResponse> disableAlert(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		likes.disableAlert(meetupId, userId);
		return ApiResult.ok(new MeetupVacancyAlertResponse(false));
	}

}
