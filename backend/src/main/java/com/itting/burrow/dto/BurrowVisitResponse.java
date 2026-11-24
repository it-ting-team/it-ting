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
		@Schema(description = "단골 여부. KAN-47 전까지 false") boolean isRegular,
		ChatResponse chat, EntranceResponse entrance) {

	public record ChatResponse(
			@Schema(description = "대화 상태. 채팅 기능이 연결되기 전까지 NONE") ChatStatus status,
			@Schema(description = "대화방 id. CHATTING이 아니면 null", nullable = true) Long chatRoomId) {
	}

	public enum ChatStatus {
		CHATTING, REQUEST_SENT, REQUEST_RECEIVED, NONE
	}

}
