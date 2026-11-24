package com.itting.burrow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record BurrowMoveRequest(
		@NotNull @Schema(description = "이번 학기에 고른 캠퍼스-층 id", example = "15") Long campusFloorId) {
}
