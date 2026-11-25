package com.itting.burrow.dto;

import java.util.List;

import com.itting.burrow.dto.MyBurrowResponse.AnswerResponse;
import com.itting.interest.dto.InterestTagResponse;
import com.itting.term.dto.TermListResponse.TermResponse;
import com.itting.user.dto.UserDisplayResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record FieldDetailResponse(TermResponse term, Long campusFloorId, String campusName, int floor,
		@Schema(description = "자리 번호 순으로 정렬한 땅굴 입구") List<FieldEntranceResponse> items) {

	public record FieldEntranceResponse(int slot, Long burrowId, boolean isMine, UserDisplayResponse owner,
			@Schema(description = "주인의 자기소개. 쓰지 않았으면 null", nullable = true) String intro,
			List<InterestTagResponse> interests, List<AnswerResponse> answers) {
	}

}
