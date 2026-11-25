package com.itting.meetup.dto;

import com.itting.global.validation.CodePointLength;

public record MeetupApplicationRequest(
		@CodePointLength(max = 100, message = "100자 이하여야 합니다.") String message) {
}
