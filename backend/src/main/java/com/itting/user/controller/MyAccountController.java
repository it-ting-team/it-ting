package com.itting.user.controller;

import com.itting.global.response.ApiResult;
import com.itting.user.dto.MyAccountResponse;
import com.itting.user.dto.PasswordChangeRequest;
import com.itting.user.service.MyAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10 설정", description = "계정 정보, 비밀번호 변경, 알림 설정, 공지사항")
@RestController
@RequestMapping("/api/v1/me")
public class MyAccountController {

	private final MyAccountService myAccountService;

	public MyAccountController(MyAccountService myAccountService) {
		this.myAccountService = myAccountService;
	}

	@Operation(summary = "계정 정보",
			description = "가입 방법(비밀번호가 있는지)과 Google 연동 상태를 준다. 비밀번호나 Google 식별자, 로그인 이메일은 주지 않고 있는지만 준다. "
					+ "설정의 화면을 열 때 부른다. 로그인이 필요하다.")
	@GetMapping("/account")
	public ApiResult<MyAccountResponse> getAccount(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			HttpServletRequest request) {
		return ApiResult.ok(myAccountService.getAccount(userId, request));
	}

	@Operation(summary = "비밀번호 변경",
			description = "비밀번호를 바꾸고 이 계정의 다른 기기를 로그아웃시킨다. 지금 쓰는 기기는 그대로 로그인돼 있다. "
					+ "비밀번호가 없는 계정(Google로만 가입)은 409 PASSWORD_NOT_SET, 지금 비밀번호가 틀리면 400 CURRENT_PASSWORD_MISMATCH, "
					+ "새 비밀번호가 지금 비밀번호와 같으면 400 PASSWORD_UNCHANGED다. 로그인이 필요하다.")
	@PatchMapping("/password")
	public ApiResult<Void> changePassword(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody PasswordChangeRequest body, HttpServletRequest request) {
		myAccountService.changePassword(userId, body, request);
		return ApiResult.ok();
	}

}
