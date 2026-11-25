package com.itting.community.dto;

import java.util.List;

import com.itting.meetup.dto.MeetupListResponse.MeetupCardResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/me/linkable-meetups}의 {@code data}.
 *
 * @param items 글에 연결할 수 있는 내 모임의 카드. 나누지 않고 모두 준다. 없으면 []
 */
public record LinkableMeetupsResponse(
		@Schema(description = "글에 연결할 수 있는 모임의 카드. 모임 홍보는 시작이 가까운 순, 모임 후기와 자유는 시작이 최근인 순. 없으면 []")
		List<MeetupCardResponse> items) {
}
