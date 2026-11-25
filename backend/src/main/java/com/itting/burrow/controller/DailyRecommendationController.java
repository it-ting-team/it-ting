package com.itting.burrow.controller;

import java.io.IOException;
import java.util.List;

import com.itting.burrow.dto.DailyRecommendationRequest;
import com.itting.burrow.dto.DailyRecommendationResponse;
import com.itting.burrow.service.DailyRecommendationService;
import com.itting.burrow.service.DailyRecommendationService.Result;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.global.response.ApiError;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Tag(name = "05 탐색 · 방문")
@RestController
@RequestMapping("/api/v1/me/daily-recommendation")
public class DailyRecommendationController {

	private final DailyRecommendationService service;
	private final ObjectReader jsonReader;
	private final Validator validator;

	public DailyRecommendationController(DailyRecommendationService service, JsonMapper jsonMapper, Validator validator) {
		this.service = service;
		this.jsonReader = jsonMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
		this.validator = validator;
	}

	@Operation(summary = "오늘의 추천 친구 조회")
	@GetMapping
	public ApiResult<DailyRecommendationResponse> get(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(service.get(userId));
	}

	@Operation(summary = "오늘의 추천 친구 받기",
			requestBody = @RequestBody(description = "오늘 처음 추천받을 때 고르는 캠퍼스-층과 기수", content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = DailyRecommendationRequest.class))))
	@PostMapping
	public ResponseEntity<ApiResult<DailyRecommendationResponse>> receive(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId, HttpServletRequest request) {
		Result result = service.receive(userId, () -> readRequest(request));
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(ApiResult.ok(result.response()));
	}

	/** 당일 확정 전의 요청에만 본문을 읽고 검증한다. */
	private DailyRecommendationRequest readRequest(HttpServletRequest request) {
		JsonNode body;
		try {
			body = jsonReader.readTree(request.getInputStream());
		}
		catch (IOException | JacksonException e) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT);
		}
		if (!body.isObject()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT);
		}
		Long floor = readNumber(body.path("campusFloorId"), "campusFloorId");
		JsonNode generationNode = body.path("generation");
		Long generation = readNumber(generationNode, "generation");
		if (generation != null && !generationNode.canConvertToInt()) {
			throw BusinessException.invalidInput("generation", "정수 범위를 벗어났습니다.");
		}
		DailyRecommendationRequest recommendation = new DailyRecommendationRequest(floor,
				generation == null ? null : generation.intValue());
		List<ApiError.FieldError> errors = validator.validate(recommendation).stream()
				.map(error -> new ApiError.FieldError(error.getPropertyPath().toString(), error.getMessage()))
				.toList();
		if (!errors.isEmpty()) {
			throw BusinessException.invalidInput(errors);
		}
		return recommendation;
	}

	private static Long readNumber(JsonNode node, String field) {
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		if (!node.isIntegralNumber() || !node.canConvertToLong()) {
			throw BusinessException.invalidInput(field, "정수를 입력해야 합니다.");
		}
		return node.longValue();
	}

}
