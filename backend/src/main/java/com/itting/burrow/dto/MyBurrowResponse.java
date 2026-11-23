package com.itting.burrow.dto;

import java.util.List;

import com.itting.interest.dto.InterestTagResponse;
import com.itting.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 땅굴과 내 정보 카드에 보여 줄 값. {@code GET /api/v1/me/burrow}와 {@code PATCH /api/v1/me/profile}의 {@code data}다.
 * 가구, 무드, 우체통의 모습은 담지 않는다. 프론트엔드가 {@code interests}와 {@code guestbookCount}로 그린다.
 */
public record MyBurrowResponse(
		@Schema(description = "내 땅굴의 id. 방명록 목록을 부를 때 쓴다", example = "18") Long burrowId,
		@Schema(description = "땅굴 주인인 나의 사용자 표시") UserSummaryResponse owner,
		@Schema(description = "프로필의 캠퍼스-층 id. 내 정보 수정에서 지금 값을 고른 채로 열 때 쓴다", example = "12") Long campusFloorId,
		@Schema(description = "자기소개. 쓰지 않았으면 null", nullable = true) String intro,
		@Schema(description = "내 관심사 태그. 고른 순서이고 첫 번째가 대표 관심사다. isShared는 모두 false") List<InterestTagResponse> interests,
		@Schema(description = "자기소개 질문과 답변 3개. 자리 순서") List<AnswerResponse> answers,
		@Schema(description = "내 땅굴을 단골로 담은 사람 수", example = "7") long regularCount,
		@Schema(description = "방명록 글 수. 신고로 가려진 글은 뺀다. 1 이상이면 우체통의 깃발을 올린다", example = "3") long guestbookCount,
		@Schema(description = "이번 학기 들판에 놓인 내 땅굴 입구. \"탐색\"을 누르면 이 들판으로 간다") EntranceResponse entrance) {

	public record AnswerResponse(
			@Schema(description = "보이는 자리. 1 ~ 3", example = "1") int slot,
			@Schema(description = "질문의 id", example = "5") Long introQuestionId,
			@Schema(description = "질문 문구", example = "운동하고 나서 꼭 먹는 것은?") String question,
			@Schema(description = "답변. 10 ~ 50자", example = "운동하고 나면 무조건 제로 콜라를 마셔요") String answer) {
	}

	public record EntranceResponse(
			@Schema(description = "그 들판의 학기 id", example = "4") Long termId,
			@Schema(description = "그 들판의 캠퍼스-층 id", example = "12") Long campusFloorId,
			@Schema(description = "그 들판에서의 자리 번호", example = "31") int slot) {
	}

}
