package com.itting.generation.controller;

import com.itting.generation.dto.GenerationListResponse;
import com.itting.generation.service.GenerationService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "00 공통", description = "여러 영역이 함께 쓰는 목록")
@RestController
@RequestMapping("/api/v1/generations")
public class GenerationController {

	private final GenerationService generationService;

	public GenerationController(GenerationService generationService) {
		this.generationService = generationService;
	}

	@Operation(summary = "기수 목록", description = "SSAFY 인증을 할 수 있는 기수를 큰 것부터 준다. 로그인 없이 부를 수 있다.")
	@GetMapping
	public ApiResult<GenerationListResponse> getGenerations() {
		return ApiResult.ok(generationService.getGenerations());
	}

}
