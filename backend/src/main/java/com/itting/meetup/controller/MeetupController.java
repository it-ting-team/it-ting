package com.itting.meetup.controller;

import java.time.LocalDate;
import java.util.List;

import com.itting.global.response.ApiResult;
import com.itting.global.validation.CodePointLength;
import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.domain.MeetupSort;
import com.itting.meetup.dto.MeetupCreateRequest;
import com.itting.meetup.dto.MeetupCreateResponse;
import com.itting.meetup.dto.MeetupDetailResponse;
import com.itting.meetup.dto.MeetupListResponse;
import com.itting.meetup.dto.MeetupUpdateRequest;
import com.itting.meetup.service.MeetupQueryService;
import com.itting.meetup.service.MeetupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
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

@Tag(name = "07 모임")
@RestController
@RequestMapping("/api/v1/meetups")
public class MeetupController {

	private final MeetupService service;
	private final MeetupQueryService queries;

	public MeetupController(MeetupService service, MeetupQueryService queries) {
		this.service = service;
		this.queries = queries;
	}

	@Operation(summary = "모임 목록")
	@GetMapping
	public ApiResult<MeetupListResponse> list(
			@RequestParam(required = false) @CodePointLength(max = 50, message = "50자 이하여야 합니다.") String keyword,
			@RequestParam(required = false) List<MeetupCategory> categories,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) MeetupMode mode,
			@RequestParam(defaultValue = "STARTS_SOON") MeetupSort sort,
			@RequestParam(defaultValue = "1") @Min(1) int page, @RequestParam(defaultValue = "6") int size) {
		return ApiResult.ok(queries.list(keyword, categories, dateFrom, dateTo, mode, sort, page, size));
	}

	@Operation(summary = "모임 만들기")
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping
	public ApiResult<MeetupCreateResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody MeetupCreateRequest request) {
		return ApiResult.ok(service.create(userId, request));
	}

	@Operation(summary = "모임 상세")
	@GetMapping("/{meetupId}")
	public ApiResult<MeetupDetailResponse> detail(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(queries.detail(meetupId, userId));
	}

	@Operation(summary = "모임 수정")
	@PatchMapping("/{meetupId}")
	public ApiResult<Void> update(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId, @RequestBody MeetupUpdateRequest request) {
		service.update(meetupId, userId, request);
		return ApiResult.ok();
	}

	@Operation(summary = "모임 취소")
	@PostMapping("/{meetupId}/cancel")
	public ApiResult<Void> cancel(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		service.cancel(meetupId, userId);
		return ApiResult.ok();
	}

	@Operation(summary = "모임 삭제")
	@DeleteMapping("/{meetupId}")
	public ApiResult<Void> delete(@PathVariable Long meetupId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		service.delete(meetupId, userId);
		return ApiResult.ok();
	}

}
