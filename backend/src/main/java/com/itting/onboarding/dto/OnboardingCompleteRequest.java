package com.itting.onboarding.dto;

import java.util.List;

import com.itting.global.validation.CodePointLength;
import com.itting.user.dto.Nickname;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * {@code POST /api/v1/me/onboarding/complete}의 본문. 이미 온보딩을 마친 계정이면 서버가 보지 않으므로,
 * 컨트롤러가 아니라 서비스가 완료 여부를 본 뒤에 검사한다.
 *
 * @param nickname 닉네임. 앞뒤 공백은 지운다
 * @param mole 두더지 아이템. 네 부위를 모두 보낸다
 * @param interestIds 고른 세부 관심사의 id. 고른 순서대로이고 첫 번째가 대표 관심사다
 * @param answers 고른 질문과 답변. 보낸 순서가 자기소개에서 보이는 순서다
 */
public record OnboardingCompleteRequest(
		@Nickname String nickname,
		@NotNull @Valid Mole mole,
		@NotNull @Size(min = 3, max = 10, message = "3개 이상 10개 이하로 골라야 합니다.")
		@UniqueElements(message = "같은 관심사를 두 번 고를 수 없습니다.") List<@NotNull Long> interestIds,
		@NotNull @Size(min = 3, max = 3, message = "질문 3개에 답해야 합니다.") List<@NotNull @Valid Answer> answers) {

	public OnboardingCompleteRequest {
		if (nickname != null) {
			nickname = nickname.strip();
		}
	}

	/**
	 * 부위마다 고른 아이템의 코드. "없음"을 고른 부위는 null이다. 서버는 코드의 모양만 보고, 아이템 목록에 있는 코드인지는 보지 않는다.
	 */
	public record Mole(
			@Pattern(regexp = ITEM_CODE, message = ITEM_CODE_MESSAGE) String head,
			@Pattern(regexp = ITEM_CODE, message = ITEM_CODE_MESSAGE) String face,
			@Pattern(regexp = ITEM_CODE, message = ITEM_CODE_MESSAGE) String neck,
			@Pattern(regexp = ITEM_CODE, message = ITEM_CODE_MESSAGE) String hand) {

		private static final String ITEM_CODE = "[A-Z0-9_]{1,30}";

		private static final String ITEM_CODE_MESSAGE = "영어 대문자, 숫자, 밑줄로 30자 이하여야 합니다.";

	}

	/**
	 * @param introQuestionId 질문의 id. 세 질문은 서로 달라야 한다
	 * @param answer 답변. 10 ~ 50자. 이모지도 한 자로 센다(DB의 {@code CHAR_LENGTH}와 같은 기준)
	 */
	public record Answer(
			@NotNull Long introQuestionId,
			@NotNull @CodePointLength(min = 10, max = 50, message = "10자 이상 50자 이하로 써야 합니다.") String answer) {
	}

}
