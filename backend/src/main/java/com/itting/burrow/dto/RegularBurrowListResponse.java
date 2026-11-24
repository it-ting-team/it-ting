package com.itting.burrow.dto;

import java.time.Instant;
import java.util.List;

import com.itting.user.dto.UserDisplayResponse;

public record RegularBurrowListResponse(List<RegularBurrowResponse> items) {

	public record RegularBurrowResponse(Long burrowId, UserDisplayResponse owner, Instant createdAt) {
	}

}
