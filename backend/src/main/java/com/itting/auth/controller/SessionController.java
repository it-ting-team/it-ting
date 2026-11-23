package com.itting.auth.controller;

import com.itting.auth.dto.LoginRequest;
import com.itting.auth.dto.LoginResponse;
import com.itting.auth.service.LoginService;
import com.itting.auth.service.LoginService.LoginResult;
import com.itting.auth.service.SessionService;
import com.itting.global.response.ApiResult;
import com.itting.global.security.AllowBeforeNewTerm;
import com.itting.global.security.AllowDuringOnboarding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "02 로그인 · 회원가입", description = "로그인, 로그아웃, 내 계정")
@AllowDuringOnboarding
@AllowBeforeNewTerm
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

	private final LoginService loginService;

	private final SessionService sessionService;

	public SessionController(LoginService loginService, SessionService sessionService) {
		this.loginService = loginService;
		this.sessionService = sessionService;
	}

	@Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인한다. 성공하면 세션 쿠키 ITTING_SESSION을 준다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		LoginResult result = loginService.login(body.email(), body.password());
		sessionService.start(request, response, result.userId(), result.role());
		return ApiResult.ok(new LoginResponse(result.userId(), result.nextStep()));
	}

	@Operation(summary = "로그아웃", description = "지금 쓰는 기기의 세션만 지운다. 로그인이 필요하다.")
	@DeleteMapping("/current")
	public ApiResult<Void> logout(HttpServletRequest request) {
		sessionService.end(request);
		return ApiResult.ok();
	}

}
