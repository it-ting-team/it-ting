package com.itting.onboarding.controller;

import com.itting.global.response.ApiResult;
import com.itting.global.security.AllowDuringOnboarding;
import com.itting.onboarding.dto.OnboardingCompleteRequest;
import com.itting.onboarding.dto.OnboardingCompleteResponse;
import com.itting.onboarding.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "03 온보딩", description = "닉네임 확인, 온보딩 확정")
@AllowDuringOnboarding
@RestController
@RequestMapping("/api/v1/me/onboarding")
public class OnboardingController {

	private final OnboardingService onboardingService;

	public OnboardingController(OnboardingService onboardingService) {
		this.onboardingService = onboardingService;
	}

	/**
	 * 본문은 {@code @Valid}로 검사하지 않는다. 이미 온보딩을 마친 계정은 본문을 보지 않고 답해야 하므로 서비스가 완료 여부를 본 뒤 검사한다.
	 */
	@Operation(summary = "온보딩 확정",
			description = "닉네임 · 두더지 · 관심사 · 질문과 답변을 한 번에 저장하고 땅굴을 만든다. "
					+ "처음 저장하면 201, 이미 온보딩을 마친 계정이 다시 부르면 본문을 보지 않고 200으로 같은 값을 준다. "
					+ "로그인이 필요하다(온보딩 중에도).")
	@ApiResponse(responseCode = "201", description = "처음 저장했다")
	@ApiResponse(responseCode = "200", description = "이미 온보딩을 마친 계정이다")
	@PostMapping("/complete")
	public ApiResult<OnboardingCompleteResponse> complete(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@RequestBody OnboardingCompleteRequest body, HttpServletResponse response) {
		OnboardingService.Result result = onboardingService.complete(userId, body);
		response.setStatus(result.created() ? HttpStatus.CREATED.value() : HttpStatus.OK.value());
		return ApiResult.ok(result.response());
	}

}
