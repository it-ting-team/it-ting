package com.itting.question.dto;

import java.util.List;

import com.itting.question.domain.IntroQuestionType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/intro-questions}의 {@code data}.
 */
public record IntroQuestionListResponse(
		@Schema(description = "질문 목록. id 순") List<IntroQuestionResponse> items) {

	public record IntroQuestionResponse(
			@Schema(description = "질문의 id. 답변을 저장하는 API의 introQuestionId에는 이 값을 보낸다", example = "21") Long id,
			@Schema(description = "INTEREST(관심사 질문), TMI 가운데 하나", example = "TMI") IntroQuestionType type,
			@Schema(description = "관심사 질문이 딸린 대분류의 id. TMI 질문은 null", example = "1",
					nullable = true) Long interestCategoryId,
			@Schema(description = "질문 문구", example = "최근에 산 것 가운데 가장 잘 산 것은?") String content) {
	}

}
