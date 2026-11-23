package com.itting.user.controller;

import com.itting.global.response.ApiResult;
import com.itting.user.dto.UserProfileResponse;
import com.itting.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "00 공통", description = "여러 영역이 함께 쓰는 목록")
@RestController
@RequestMapping("/api/v1/users")
public class UserProfileController {

	private final UserProfileService userProfileService;

	public UserProfileController(UserProfileService userProfileService) {
		this.userProfileService = userProfileService;
	}

	@Operation(summary = "프로필 팝업",
			description = "사용자 표시를 눌렀을 때 뜨는 프로필 팝업의 내용을 준다. 가입을 모두 마친 사람만 주고, "
					+ "탈퇴했거나 가입 중인 사람은 404 USER_NOT_FOUND다. isShared는 요청한 사람 기준이다. 로그인이 필요하다.")
	@GetMapping("/{userId}/profile")
	public ApiResult<UserProfileResponse> getProfile(@PathVariable Long userId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long viewerId) {
		return ApiResult.ok(userProfileService.getProfile(userId, viewerId));
	}

}
