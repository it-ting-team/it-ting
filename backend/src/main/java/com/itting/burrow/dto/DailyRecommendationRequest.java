package com.itting.burrow.dto;

import jakarta.validation.constraints.NotNull;

public record DailyRecommendationRequest(@NotNull Long campusFloorId, @NotNull Integer generation) {
}
