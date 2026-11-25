package com.itting.meetup.controller;

import java.time.YearMonth;

import com.itting.global.response.ApiResult;
import com.itting.meetup.domain.MeetupCalendarScope;
import com.itting.meetup.domain.MyMeetupTab;
import com.itting.meetup.dto.MeetupCalendarResponse;
import com.itting.meetup.dto.MyMeetupResponse;
import com.itting.meetup.dto.MyMeetupSummaryResponse;
import com.itting.meetup.service.MeetupDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "07 모임")
@RestController
@RequestMapping("/api/v1")
public class MeetupDashboardController {

	private final MeetupDashboardService dashboard;

	public MeetupDashboardController(MeetupDashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@Operation(summary = "내 모임 탭")
	@GetMapping("/me/meetups")
	public ApiResult<MyMeetupResponse> myMeetups(@RequestParam MyMeetupTab tab,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(dashboard.myMeetups(userId, tab));
	}

	@Operation(summary = "내 모임 요약")
	@GetMapping("/me/meetups/summary")
	public ApiResult<MyMeetupSummaryResponse> summary(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(dashboard.summary(userId));
	}

	@Operation(summary = "모임 캘린더")
	@GetMapping("/meetup-calendar")
	public ApiResult<MeetupCalendarResponse> calendar(@RequestParam @Min(1000) @Max(9999) int year,
			@RequestParam @Min(1) @Max(12) int month, @RequestParam(defaultValue = "MINE") MeetupCalendarScope scope,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(dashboard.calendar(userId, YearMonth.of(year, month), scope));
	}

}
