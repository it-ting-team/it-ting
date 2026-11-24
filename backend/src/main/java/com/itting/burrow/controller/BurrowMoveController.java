package com.itting.burrow.controller;

import java.io.IOException;
import java.util.List;

import com.itting.burrow.dto.BurrowMoveRequest;
import com.itting.burrow.dto.BurrowMoveResponse;
import com.itting.burrow.service.BurrowMoveService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.global.response.ApiError;
import com.itting.global.response.ApiResult;
import com.itting.global.security.AllowBeforeNewTerm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Tag(name = "05 탐색 · 방문")
@RestController
public class BurrowMoveController {

	private final BurrowMoveService moveService;
	private final ObjectReader jsonReader;
	private final Validator validator;

	public BurrowMoveController(BurrowMoveService moveService, JsonMapper jsonMapper, Validator validator) {
		this.moveService = moveService;
		this.jsonReader = jsonMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
		this.validator = validator;
	}

	@Operation(summary = "새 학기 이사", description = "이번 학기에 이사한다. 이미 이사했으면 현재 값을 준다.",
			requestBody = @RequestBody(description = "첫 이사에 필요한 목적지 층", content = @Content(
					mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = BurrowMoveRequest.class))))
	@AllowBeforeNewTerm
	@PostMapping("/api/v1/me/burrow/move")
	public ApiResult<BurrowMoveResponse> move(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			HttpServletRequest request) {
		return ApiResult.ok(moveService.move(userId, () -> readRequest(request)));
	}

	/** 재호출에서는 잘못된 본문도 무시해야 하므로 첫 이사에만 읽고 검증한다. */
	private BurrowMoveRequest readRequest(HttpServletRequest request) {
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
		JsonNode floor = body.path("campusFloorId");
		Long campusFloorId = null;
		if (!floor.isMissingNode() && !floor.isNull()) {
			if (!floor.isIntegralNumber() || !floor.canConvertToLong()) {
				throw BusinessException.invalidInput("campusFloorId", "정수 id를 입력해야 합니다.");
			}
			campusFloorId = floor.longValue();
		}
		BurrowMoveRequest moveRequest = new BurrowMoveRequest(campusFloorId);
		List<ApiError.FieldError> errors = validator.validate(moveRequest).stream()
				.map(error -> new ApiError.FieldError(error.getPropertyPath().toString(), error.getMessage()))
				.toList();
		if (!errors.isEmpty()) {
			throw BusinessException.invalidInput(errors);
		}
		return moveRequest;
	}

}
