package com.itting.user.controller;

import com.itting.global.response.ApiResult;
import com.itting.global.security.AllowDuringOnboarding;
import com.itting.user.dto.NicknameCheckRequest;
import com.itting.user.dto.NicknameCheckResponse;
import com.itting.user.service.NicknameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "03 온보딩", description = "닉네임 확인, 온보딩 확정")
@AllowDuringOnboarding
@RestController
@RequestMapping("/api/v1/nicknames")
public class NicknameController {

	private final NicknameService nicknameService;

	public NicknameController(NicknameService nicknameService) {
		this.nicknameService = nicknameService;
	}

	@Operation(summary = "닉네임 확인",
			description = "닉네임을 쓸 수 있는지 확인하고, 쓸 수 있으면 이 계정 몫으로 5분 동안 맡아 둔다. "
					+ "한글 · 영어 · 숫자로 1 ~ 12자이고 앞뒤 공백은 지운다. 영어 대소문자만 다른 닉네임은 같은 것으로 본다. "
					+ "로그인이 필요하다(온보딩 중에도).")
	@PostMapping("/check")
	public ApiResult<NicknameCheckResponse> check(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody NicknameCheckRequest body) {
		return ApiResult.ok(nicknameService.check(userId, body.nickname()));
	}

}
