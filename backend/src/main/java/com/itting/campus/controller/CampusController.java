package com.itting.campus.controller;

import com.itting.campus.dto.CampusListResponse;
import com.itting.campus.service.CampusService;
import com.itting.global.response.ApiResult;
import com.itting.global.security.AllowBeforeNewTerm;
import com.itting.global.security.AllowDuringOnboarding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "00 공통", description = "여러 영역이 함께 쓰는 목록")
@AllowDuringOnboarding
@AllowBeforeNewTerm
@RestController
@RequestMapping("/api/v1/campuses")
public class CampusController {

	private final CampusService campusService;

	public CampusController(CampusService campusService) {
		this.campusService = campusService;
	}

	@Operation(summary = "캠퍼스와 층 목록", description = "지우지 않은 캠퍼스와 층을 모두 준다. 로그인 없이 부를 수 있다.")
	@GetMapping
	public ApiResult<CampusListResponse> getCampuses() {
		return ApiResult.ok(campusService.getCampuses());
	}

}
