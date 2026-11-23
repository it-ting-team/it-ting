package com.itting.support;

import com.itting.global.response.ApiResult;
import com.itting.global.security.AllowBeforeNewTerm;
import com.itting.global.security.AllowDuringOnboarding;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 온보딩 · 새 학기를 마치지 않은 계정을 막는 장치를 검증하려고 테스트에서만 띄우는 컨트롤러다.
 * 표시를 붙이지 않은 API, 한쪽만 붙인 API를 둔다.
 */
@RestController
@RequestMapping("/api/v1/step-samples")
public class AccountStepSampleController {

	/** 표시가 없다. 온보딩 · 새 학기를 마친 계정만 부를 수 있다. */
	@GetMapping
	ApiResult<Void> plain() {
		return ApiResult.ok();
	}

	@AllowDuringOnboarding
	@GetMapping("/onboarding")
	ApiResult<Void> duringOnboarding() {
		return ApiResult.ok();
	}

	@AllowBeforeNewTerm
	@GetMapping("/new-term")
	ApiResult<Void> beforeNewTerm() {
		return ApiResult.ok();
	}

}
