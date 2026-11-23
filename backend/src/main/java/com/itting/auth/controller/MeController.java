package com.itting.auth.controller;

import com.itting.auth.dto.MeResponse;
import com.itting.auth.service.MeService;
import com.itting.auth.service.SessionService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.global.response.ApiResult;
import com.itting.global.security.AllowBeforeNewTerm;
import com.itting.global.security.AllowDuringOnboarding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "02 로그인 · 회원가입", description = "로그인, 로그아웃, 내 계정")
@AllowDuringOnboarding
@AllowBeforeNewTerm
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

	private final MeService meService;

	private final SessionService sessionService;

	public MeController(MeService meService, SessionService sessionService) {
		this.meService = meService;
		this.sessionService = sessionService;
	}

	@Operation(summary = "내 계정", description = "로그인 상태, 다음에 갈 화면, 소속(캠퍼스 · 기수 · 층)을 준다. 로그인하지 않았으면 401이다.")
	@GetMapping
	public ApiResult<MeResponse> getMe(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			HttpServletRequest request) {
		return meService.findMe(userId)
				.map(ApiResult::ok)
				.orElseGet(() -> {
					// 계정이 없거나 탈퇴했는데 세션만 남았다. 세션을 지우고 로그인하지 않은 것으로 답한다.
					sessionService.end(request);
					throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
				});
	}

}
