package com.itting.burrow.dto;

import com.itting.auth.domain.NextStep;
import com.itting.burrow.dto.MyBurrowResponse.EntranceResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record BurrowMoveResponse(Long userId,
		@Schema(description = "이사 뒤에는 HOME", allowableValues = "HOME") NextStep nextStep,
		EntranceResponse entrance) {
}
