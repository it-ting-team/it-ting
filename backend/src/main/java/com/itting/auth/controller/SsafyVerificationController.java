package com.itting.auth.controller;

import com.itting.auth.dto.SsafyVerificationRequest;
import com.itting.auth.dto.VerificationCodeSentResponse;
import com.itting.auth.service.SignupCookieService;
import com.itting.auth.service.SsafyVerificationService;
import com.itting.global.response.ApiResult;
import com.itting.global.security.AllowBeforeNewTerm;
import com.itting.global.security.AllowDuringOnboarding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/ssafy-verifications")
public class SsafyVerificationController {

	private final SsafyVerificationService ssafyVerificationService;

	public SsafyVerificationController(SsafyVerificationService ssafyVerificationService) {
		this.ssafyVerificationService = ssafyVerificationService;
	}

	@Operation(summary = "SSAFY 인증 코드 보내기",
			description = "고른 기수의 Mattermost로 그 아이디에게 코드를 DM으로 보낸다. 가입 쿠키가 있어야 한다. "
					+ "아이디가 틀려 메시지가 가지 않아도 같은 응답이다. 재전송은 60초 뒤부터.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResult<VerificationCodeSentResponse> send(@Valid @RequestBody SsafyVerificationRequest body,
			@Parameter(hidden = true) @CookieValue(name = SignupCookieService.COOKIE_NAME, required = false) String signupToken) {
		return ApiResult.ok(new VerificationCodeSentResponse(
				ssafyVerificationService.send(signupToken, body.generation(), body.mattermostId())));
	}

}
