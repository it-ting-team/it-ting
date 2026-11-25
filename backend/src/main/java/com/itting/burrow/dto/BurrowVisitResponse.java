package com.itting.burrow.dto;

import java.util.List;

import com.itting.burrow.dto.MyBurrowResponse.AnswerResponse;
import com.itting.burrow.dto.MyBurrowResponse.EntranceResponse;
import com.itting.interest.dto.InterestTagResponse;
import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record BurrowVisitResponse(Long burrowId, boolean isMine, UserDisplayResponse owner,
		@Schema(description = "주인의 자기소개. 쓰지 않았으면 null", nullable = true) String intro,
		List<InterestTagResponse> interests, List<AnswerResponse> answers, long guestbookCount,
		@Schema(description = "요청자가 이 땅굴을 단골로 담았는지") boolean isRegular,
		ChatResponse chat, EntranceResponse entrance) {

	public record ChatResponse(
			@Schema(description = "대화 상태. 내 땅굴이면 NONE") ChatStatus status,
			@Schema(description = "대화방 id. CHATTING이 아니면 null", nullable = true) Long chatRoomId) {
	}

	public enum ChatStatus {
		CHATTING, REQUEST_SENT, REQUEST_RECEIVED, NONE
	}

}
