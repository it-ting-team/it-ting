package com.itting.onboarding.dto;

import com.itting.auth.domain.NextStep;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/me/onboarding/complete}의 응답. 처음 저장했을 때와 다시 불렀을 때가 같다.
 *
 * @param userId 계정의 id
 * @param nextStep 항상 {@code HOME}
 * @param burrowId 만든 땅굴의 id
 */
public record OnboardingCompleteResponse(
		@Schema(example = "31") Long userId,
		@Schema(example = "HOME") NextStep nextStep,
		@Schema(example = "18") Long burrowId) {
}
