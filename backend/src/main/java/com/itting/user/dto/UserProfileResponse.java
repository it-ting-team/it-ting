package com.itting.user.dto;

import java.util.List;

import com.itting.interest.dto.InterestTagResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/users/{userId}/profile}의 {@code data}. 프로필 팝업에 보여 줄 값이다.
 * 자기소개, 질문과 답변은 담지 않는다. 그 값은 땅굴의 방노트에서 보여 준다.
 */
public record UserProfileResponse(
		@Schema(description = "그 사람의 사용자 표시") UserSummaryResponse user,
		@Schema(description = "그 사람이 고른 관심사 태그. 고른 순서이고 첫 번째가 대표 관심사다. isShared는 요청한 사람 기준") List<InterestTagResponse> interests) {
}
