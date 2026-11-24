package com.itting.burrow.controller;

import com.itting.burrow.dto.RegularBurrowListResponse;
import com.itting.burrow.dto.RegularBurrowStatusResponse;
import com.itting.burrow.service.RegularBurrowService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "05 탐색 · 방문")
@RestController
@RequestMapping("/api/v1")
public class RegularBurrowController {

	private final RegularBurrowService service;

	public RegularBurrowController(RegularBurrowService service) {
		this.service = service;
	}

	@Operation(summary = "내 단골 땅굴 목록")
	@GetMapping("/me/regular-burrows")
	public ApiResult<RegularBurrowListResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(service.list(userId));
	}

	@Operation(summary = "단골 땅굴 담기")
	@PutMapping("/burrows/{burrowId}/regular")
	public ApiResult<RegularBurrowStatusResponse> add(@PathVariable Long burrowId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		service.add(userId, burrowId);
		return ApiResult.ok(new RegularBurrowStatusResponse(true));
	}

	@Operation(summary = "단골 땅굴 해제")
	@DeleteMapping("/burrows/{burrowId}/regular")
	public ApiResult<RegularBurrowStatusResponse> remove(@PathVariable Long burrowId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		service.remove(userId, burrowId);
		return ApiResult.ok(new RegularBurrowStatusResponse(false));
	}

}
