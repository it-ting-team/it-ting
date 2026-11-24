package com.itting.burrow.controller;

import com.itting.burrow.dto.BurrowVisitResponse;
import com.itting.burrow.dto.FieldDetailResponse;
import com.itting.burrow.dto.FieldListResponse;
import com.itting.burrow.service.BurrowExploreService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "05 탐색 · 방문", description = "들판 조회, 새 학기 이사, 땅굴 방문")
@RestController
@RequestMapping("/api/v1")
public class BurrowExploreController {

	private final BurrowExploreService exploreService;

	public BurrowExploreController(BurrowExploreService exploreService) {
		this.exploreService = exploreService;
	}

	@Operation(summary = "학기별 들판 목록", description = "선택할 수 있는 캠퍼스-층과 각 들판이 비어 있는지를 준다.")
	@GetMapping("/terms/{termId}/fields")
	public ApiResult<FieldListResponse> getFields(@PathVariable Long termId) {
		return ApiResult.ok(exploreService.getFields(termId));
	}

	@Operation(summary = "들판과 입구 조회", description = "들판 한 곳의 모든 입구와 방노트의 내용을 자리 번호 순으로 준다.")
	@GetMapping("/terms/{termId}/fields/{campusFloorId}")
	public ApiResult<FieldDetailResponse> getField(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long termId, @PathVariable Long campusFloorId) {
		return ApiResult.ok(exploreService.getField(userId, termId, campusFloorId));
	}

	@Operation(summary = "땅굴 방문", description = "방문 화면과 방노트의 값, 공개 방명록 글 수를 준다.")
	@GetMapping("/users/{userId}/burrow")
	public ApiResult<BurrowVisitResponse> visit(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable("userId") Long ownerId) {
		return ApiResult.ok(exploreService.visit(userId, ownerId));
	}

}
