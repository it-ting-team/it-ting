package com.itting.user.controller;

import com.itting.burrow.dto.MyBurrowResponse;
import com.itting.global.response.ApiResult;
import com.itting.user.dto.MoleResponse;
import com.itting.user.dto.MoleUpdateRequest;
import com.itting.user.dto.ProfileUpdateRequest;
import com.itting.user.service.MyProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "04 내 땅굴", description = "내 땅굴 조회, 두더지 · 내 정보 저장, 방명록 목록 · 삭제")
@RestController
@RequestMapping("/api/v1/me")
public class MyProfileController {

	private final MyProfileService myProfileService;

	public MyProfileController(MyProfileService myProfileService) {
		this.myProfileService = myProfileService;
	}

	@Operation(summary = "두더지 저장",
			description = "두더지가 걸친 아이템을 바꾼다. 네 부위의 키를 모두 보내고, \"없음\"인 부위는 null을 보낸다. "
					+ "키가 빠지면 400이다. 로그인이 필요하다.")
	@PatchMapping("/mole")
	public ApiResult<MoleResponse> changeMole(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody MoleUpdateRequest body) {
		return ApiResult.ok(myProfileService.changeMole(userId, body));
	}

	/**
	 * 본문은 서비스가 검사한다. 세 질문이 서로 다른지까지 함께 보고 어긋난 것을 {@code fields}에 모두 담기 위해서다.
	 */
	@Operation(summary = "내 정보 저장",
			description = "닉네임, 캠퍼스-층, 관심사, 질문과 답변, 자기소개를 한 번에 저장하고 바뀐 내 땅굴 전체를 준다. "
					+ "다섯 값의 키를 모두 보낸다. 캠퍼스-층을 바꾸면 땅굴 입구가 이번 학기의 새 층 들판 다음 자리로 옮겨진다. "
					+ "로그인이 필요하다.")
	@PatchMapping("/profile")
	public ApiResult<MyBurrowResponse> changeProfile(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@RequestBody ProfileUpdateRequest body) {
		return ApiResult.ok(myProfileService.changeProfile(userId, body));
	}

}
