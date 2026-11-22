package com.itting.interest.controller;

import com.itting.global.response.ApiResult;
import com.itting.interest.dto.InterestCategoryListResponse;
import com.itting.interest.service.InterestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "00 공통", description = "여러 영역이 함께 쓰는 목록")
@RestController
@RequestMapping("/api/v1/interest-categories")
public class InterestCategoryController {

	private final InterestService interestService;

	public InterestCategoryController(InterestService interestService) {
		this.interestService = interestService;
	}

	@Operation(summary = "관심사 대분류와 세부 관심사",
			description = "지우지 않은 대분류와 세부 관심사를 id 순으로, 세부 관심사마다 고른 사람 수와 함께 준다. 로그인이 필요하다(온보딩 중에도).")
	@GetMapping
	public ApiResult<InterestCategoryListResponse> getInterestCategories() {
		return ApiResult.ok(interestService.getInterestCategories());
	}

}
