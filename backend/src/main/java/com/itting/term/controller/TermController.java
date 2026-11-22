package com.itting.term.controller;

import com.itting.global.response.ApiResult;
import com.itting.term.dto.TermListResponse;
import com.itting.term.service.TermService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "00 공통", description = "여러 영역이 함께 쓰는 목록")
@RestController
@RequestMapping("/api/v1/terms")
public class TermController {

	private final TermService termService;

	public TermController(TermService termService) {
		this.termService = termService;
	}

	@Operation(summary = "학기 목록", description = "이미 열린 학기를 최근에 열린 것부터 준다. 첫 번째가 이번 학기다. 로그인이 필요하다.")
	@GetMapping
	public ApiResult<TermListResponse> getTerms() {
		return ApiResult.ok(termService.getTerms());
	}

}
