package com.itting.auth.controller;

import com.itting.auth.dto.PasswordResetCompleteRequest;
import com.itting.auth.dto.PasswordResetRequest;
import com.itting.auth.service.PasswordResetService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "02 로그인 · 회원가입", description = "로그인, 로그아웃, 내 계정, 회원가입, 비밀번호 재설정")
@RestController
@RequestMapping("/api/v1/password-resets")
public class PasswordResetController {

	private final PasswordResetService passwordResetService;

	public PasswordResetController(PasswordResetService passwordResetService) {
		this.passwordResetService = passwordResetService;
	}

	@Operation(summary = "비밀번호 재설정 메일 보내기",
			description = "가입 여부가 드러나지 않도록 어떤 이메일이든 201로 답한다. 메일은 응답 뒤에 보내고, 같은 이메일로 60초 안에 다시 요청하면 보내지 않는다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResult<Void> request(@Valid @RequestBody PasswordResetRequest body) {
		passwordResetService.request(body.email());
		return ApiResult.ok();
	}

	@Operation(summary = "재설정 링크 확인", description = "링크가 유효하면 200, 30분이 지났거나 이미 썼거나 없는 링크면 404다. 링크를 지우지 않는다.")
	@GetMapping("/{token}")
	public ApiResult<Void> check(@PathVariable String token) {
		passwordResetService.check(token);
		return ApiResult.ok();
	}

	@Operation(summary = "새 비밀번호 저장",
			description = "새 비밀번호를 저장하고 링크를 지운다. 그 계정의 모든 기기에서 로그아웃된다. 로그인시키지 않는다.")
	@PostMapping("/{token}/complete")
	public ApiResult<Void> complete(@PathVariable String token, @Valid @RequestBody PasswordResetCompleteRequest body) {
		passwordResetService.complete(token, body.password());
		return ApiResult.ok();
	}

}
