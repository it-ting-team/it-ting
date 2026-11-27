package com.itting.user.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code GET /api/v1/me/withdrawal-check}의 {@code data}. */
public record WithdrawalCheckResponse(
		@Schema(description = "탈퇴를 막는 모임이 없는지. hostingMeetups가 비어 있으면 true", example = "false") boolean canWithdraw,
		@Schema(description = "탈퇴를 막는, 내가 연 예정 모임. 시작이 가까운 것부터") List<HostingMeetupResponse> hostingMeetups,
		@Schema(description = "탈퇴를 위한 Google 확인이 끝나 아직 유효한지. 비밀번호가 있는 계정은 항상 false", example = "false")
		boolean isGoogleVerified) {

	public record HostingMeetupResponse(@Schema(description = "모임의 id", example = "19") Long id,
			@Schema(description = "모임 이름", example = "토요일 아침 한강 러닝") String title,
			@Schema(description = "시작 시각", example = "2026-11-28T00:00:00Z") Instant startsAt) {
	}

}
