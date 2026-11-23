package com.itting.auth.controller;

import com.itting.auth.domain.NextStep;
import com.itting.auth.dto.SignupRequest;
import com.itting.auth.dto.SignupResponse;
import com.itting.auth.service.SessionService;
import com.itting.auth.service.SignupCookieService;
import com.itting.auth.service.SignupService;
import com.itting.global.response.ApiResult;
import com.itting.global.security.AllowBeforeNewTerm;
import com.itting.global.security.AllowDuringOnboarding;
import com.itting.user.domain.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "02 로그인 · 회원가입", description = "로그인, 로그아웃, 내 계정, 회원가입")
@AllowDuringOnboarding
@AllowBeforeNewTerm
@RestController
@RequestMapping("/api/v1/users")
public class SignupController {

	private final SignupService signupService;

	private final SignupCookieService signupCookieService;

	private final SessionService sessionService;

	public SignupController(SignupService signupService, SignupCookieService signupCookieService,
			SessionService sessionService) {
		this.signupService = signupService;
		this.signupCookieService = signupCookieService;
		this.sessionService = sessionService;
	}

	@Operation(summary = "계정 만들기(땅굴 파기)",
			description = "SSAFY 인증 코드를 확인하고 계정과 프로필을 만든 뒤 로그인시킨다. 가입 쿠키가 있어야 한다. "
					+ "성공하면 가입 쿠키를 지우고 세션 쿠키 ITTING_SESSION을 준다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResult<SignupResponse> signUp(@Valid @RequestBody SignupRequest body,
			@Parameter(hidden = true) @CookieValue(name = SignupCookieService.COOKIE_NAME, required = false) String signupToken,
			HttpServletRequest request, HttpServletResponse response) {
		Long userId = signupService.signUp(signupToken, body.password(), body.code(), body.campusFloorId());
		signupCookieService.expire(response);
		sessionService.start(request, response, userId, UserRole.USER);
		return ApiResult.ok(new SignupResponse(userId, NextStep.ONBOARDING));
	}

}
