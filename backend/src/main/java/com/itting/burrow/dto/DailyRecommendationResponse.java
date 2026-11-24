package com.itting.burrow.dto;

import java.time.LocalDate;
import java.util.List;

import com.itting.burrow.dto.MyBurrowResponse.AnswerResponse;
import com.itting.interest.dto.InterestTagResponse;
import com.itting.user.dto.UserDisplayResponse;

public record DailyRecommendationResponse(boolean hasReceived, LocalDate recommendedOn,
		List<RecommendationCardResponse> items) {

	public record RecommendationCardResponse(Long burrowId, UserDisplayResponse owner,
			List<InterestTagResponse> interests, List<AnswerResponse> answers) {
	}

}
