package com.itting.meetup.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.itting.global.validation.CodePointLength;
import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MeetupUpdateRequest(
		@NotBlank @CodePointLength(max = 30, message = "30자 이하여야 합니다.") String title,
		@NotNull MeetupCategory category,
		@JsonProperty(required = true) @CodePointLength(max = 1000, message = "1,000자 이하여야 합니다.") String description,
		@NotNull @Min(2) @Max(50) Integer capacity,
		@JsonProperty(required = true) @CodePointLength(max = 100, message = "100자 이하여야 합니다.") String supplies,
		@NotNull Boolean isMessageEnabled,
		@NotNull Instant startsAt,
		@JsonProperty(required = true) Instant endsAt,
		@NotNull MeetupMode mode,
		@JsonProperty(required = true) @CodePointLength(max = 100, message = "100자 이하여야 합니다.") String place,
		@JsonProperty(required = true) @CodePointLength(max = 100, message = "100자 이하여야 합니다.") String placeDetail,
		@JsonProperty(required = true) @CodePointLength(max = 500, message = "500자 이하여야 합니다.") String joinUrl) implements MeetupInput {
}
