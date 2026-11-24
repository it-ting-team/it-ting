package com.itting.burrow.controller;

import com.itting.burrow.dto.MyBurrowResponse;
import com.itting.burrow.service.MyBurrowService;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "04 내 땅굴", description = "내 땅굴 조회, 두더지 · 내 정보 저장, 방명록 목록 · 삭제")
@RestController
@RequestMapping("/api/v1/me/burrow")
public class MyBurrowController {

	private final MyBurrowService myBurrowService;

	public MyBurrowController(MyBurrowService myBurrowService) {
		this.myBurrowService = myBurrowService;
	}

	@Operation(summary = "내 땅굴",
			description = "내 땅굴과 내 정보 카드에 보여 줄 값을 한 번에 준다. 꾸미기 모드와 내 정보 수정도 이 값을 지금 값으로 쓴다. "
					+ "로그인이 필요하다.")
	@GetMapping
	public ApiResult<MyBurrowResponse> getMyBurrow(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(myBurrowService.getMyBurrow(userId));
	}

}
