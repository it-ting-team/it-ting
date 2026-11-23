package com.itting.question.controller;

import com.itting.global.response.ApiResult;
import com.itting.global.security.AllowBeforeNewTerm;
import com.itting.global.security.AllowDuringOnboarding;
import com.itting.question.domain.IntroQuestionType;
import com.itting.question.dto.IntroQuestionListResponse;
import com.itting.question.service.IntroQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "00 공통", description = "여러 영역이 함께 쓰는 목록")
@AllowDuringOnboarding
@AllowBeforeNewTerm
@RestController
@RequestMapping("/api/v1/intro-questions")
public class IntroQuestionController {

	private final IntroQuestionService introQuestionService;

	public IntroQuestionController(IntroQuestionService introQuestionService) {
		this.introQuestionService = introQuestionService;
	}

	@Operation(summary = "자기소개 질문 목록",
			description = "질문을 id 순으로 모두 준다. type을 보내면 그 종류만 준다. 로그인이 필요하다(온보딩 중에도).")
	@GetMapping
	public ApiResult<IntroQuestionListResponse> getIntroQuestions(
			@Parameter(description = "INTEREST(관심사 질문), TMI 가운데 하나. 보내지 않으면 두 종류를 모두 준다")
			@RequestParam(required = false) IntroQuestionType type) {
		return ApiResult.ok(introQuestionService.getIntroQuestions(type));
	}

}
