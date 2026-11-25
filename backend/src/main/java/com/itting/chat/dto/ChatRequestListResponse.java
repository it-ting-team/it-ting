package com.itting.chat.dto;

import java.time.Instant;
import java.util.List;

import com.itting.interest.dto.InterestTagResponse;
import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/me/chat-requests}의 {@code data}.
 */
public record ChatRequestListResponse(
		@Schema(description = "대화 요청. 새로 보낸 것부터. 한 번에 모두 준다") List<ChatRequestResponse> items) {

	/**
	 * 대화 요청 한 건. 보낸 요청에는 거절됐는지 알려 주는 값이 없다(거절은 보낸 사람에게 드러나지 않는다).
	 */
	public record ChatRequestResponse(
			@Schema(description = "대화 요청의 id. 수락, 거절, 취소를 부를 때 쓴다", example = "91") Long id,
			@Schema(description = "상대의 사용자 표시. 받은 요청이면 보낸 사람, 보낸 요청이면 받는 사람") UserDisplayResponse counterpart,
			@Schema(description = "요청에 붙인 인사. 없으면 null", example = "보드게임 좋아하신다고 해서요. 같이 해요!") String message,
			@Schema(description = "상대와 내가 함께 고른 관심사 태그. 겹치는 것만, 상대가 고른 순서. 없으면 []")
			List<InterestTagResponse> sharedInterests,
			@Schema(description = "보낸 시각") Instant createdAt,
			@Schema(description = "만료되는 시각. 보낸 시각에 5일을 더한 시각") Instant expiresAt) {
	}

}
