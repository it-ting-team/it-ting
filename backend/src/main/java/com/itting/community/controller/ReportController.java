package com.itting.community.controller;

import com.itting.community.dto.ReportCreateRequest;
import com.itting.community.dto.ReportCreateResponse;
import com.itting.community.service.ReportService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고({@code docs/api/08-community.md}). 로그인이 필요하고, 온보딩과 이번 학기 고르기를 마친 계정만 부른다.
 */
@Tag(name = "08 커뮤니티", description = "글 · 댓글 · 좋아요 · 저장")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@Operation(summary = "신고",
			description = "글 · 댓글 · 사용자 · 방명록 글을 신고한다. 같은 대상은 한 번만 신고할 수 있다. "
					+ "글 · 댓글 · 방명록 글은 마지막 복구 뒤에 서로 다른 3명이 신고하면 가려지고 작성자에게 알림이 간다"
					+ "(신고한 사람은 담지 않는다). 사용자 신고는 자동으로 조치하지 않는다. 로그인이 필요하다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResult<ReportCreateResponse> report(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody ReportCreateRequest request) {
		return ApiResult.ok(reportService.report(userId, request));
	}

}
