package com.itting.auth.controller;

import com.itting.auth.dto.EmailVerificationCheckRequest;
import com.itting.auth.dto.EmailVerificationRequest;
import com.itting.auth.dto.VerificationCodeSentResponse;
import com.itting.auth.service.EmailVerificationService;
import com.itting.auth.service.SignupCookieService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RestController
@RequestMapping("/api/v1/email-verifications")
public class EmailVerificationController {

	private final EmailVerificationService emailVerificationService;

	private final SignupCookieService signupCookieService;

	public EmailVerificationController(EmailVerificationService emailVerificationService,
			SignupCookieService signupCookieService) {
		this.emailVerificationService = emailVerificationService;
		this.signupCookieService = signupCookieService;
	}

	@Operation(summary = "이메일 인증 코드 보내기",
			description = "가입할 이메일로 숫자 6자리 코드를 보낸다. 다시 부르면 새 코드를 보내고 이전 코드는 쓸 수 없다. 재전송은 60초 뒤부터.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResult<VerificationCodeSentResponse> send(@Valid @RequestBody EmailVerificationRequest body) {
		return ApiResult.ok(new VerificationCodeSentResponse(emailVerificationService.send(body.email())));
	}

	@Operation(summary = "이메일 인증 코드 확인",
			description = "코드가 맞으면 가입 진행을 시작하고 가입 쿠키 ITTING_SIGNUP을 준다. 5번 틀리면 코드를 다시 받아야 한다.")
	@PostMapping("/check")
	public ApiResult<Void> check(@Valid @RequestBody EmailVerificationCheckRequest body,
			@Parameter(hidden = true) @CookieValue(name = SignupCookieService.COOKIE_NAME, required = false) String previousToken,
			HttpServletResponse response) {
		String token = emailVerificationService.check(body.email(), body.code(), previousToken);
		signupCookieService.write(response, token);
		return ApiResult.ok();
	}

}
