package com.itting.user.dto;

import java.util.List;
import java.util.Optional;

import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.valueextraction.Unwrapping;
import org.hibernate.validator.constraints.UniqueElements;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code PATCH /api/v1/me/profile}의 본문. 다섯 값의 키를 모두 보낸다. 바꾸지 않은 값도 지금 값을 담는다.
 * 두더지는 받지 않는다({@code PATCH /api/v1/me/mole}).
 *
 * @param nickname 닉네임. 앞뒤 공백은 지운다. 규칙은 온보딩과 같다
 * @param campusFloorId 캠퍼스-층의 id. 기수는 바꿀 수 없어 받지 않는다
 * @param interestIds 세부 관심사의 id. 보낸 순서로 저장하고 첫 번째가 대표 관심사다
 * @param answers 질문과 답변. 보낸 순서가 자리 순서다
 * @param intro 자기소개. 키가 빠지면 null이고, {@code "intro": null}이면 빈 {@link Optional}이다({@link KeyRequiredStringDeserializer}).
 *        앞뒤 공백을 지우고, 지운 뒤 비었으면 빈 {@link Optional}(비움)로 본다
 */
public record ProfileUpdateRequest(
		@Schema(description = "닉네임. 한글 · 영어 · 숫자로 1 ~ 12자", example = "두더지왕", requiredMode = Schema.RequiredMode.REQUIRED)
		@Nickname String nickname,
		@Schema(description = "캠퍼스-층의 id. GET /api/v1/campuses의 floors[].id", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull Long campusFloorId,
		@Schema(description = "세부 관심사의 id. 3 ~ 10개, 겹치지 않게. 첫 번째가 대표 관심사", example = "[47, 12, 5, 30]", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull @Size(min = 3, max = 10, message = "3개 이상 10개 이하로 골라야 합니다.")
		@UniqueElements(message = "같은 관심사를 두 번 고를 수 없습니다.") List<@NotNull Long> interestIds,
		@Schema(description = "질문과 답변. 정확히 3개이고 보낸 순서가 자리 순서. 세 질문은 서로 달라야 한다", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull @Size(min = 3, max = 3, message = "질문 3개에 답해야 합니다.") List<@NotNull @Valid Answer> answers,
		@Schema(description = "자기소개. 150자 이하. 쓰지 않았으면 null. null이나 공백뿐이면 비운다", example = "달리기와 음악을 좋아하는 14기입니다.", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED, implementation = String.class)
		@JsonDeserialize(using = KeyRequiredStringDeserializer.class)
		@NotNull(message = "필수 값입니다. 쓰지 않았으면 null을 보내세요.", payload = Unwrapping.Skip.class)
		Optional<@CodePointLength(max = 150, message = "150자 이하로 써야 합니다.") String> intro) {

	public ProfileUpdateRequest {
		if (nickname != null) {
			nickname = nickname.strip();
		}
		if (intro != null) {
			intro = intro.map(String::strip).filter(text -> !text.isEmpty());
		}
	}

	/**
	 * @param introQuestionId 질문의 id. 세 질문은 서로 달라야 한다
	 * @param answer 답변. 10 ~ 50자. 이모지도 한 자로 센다(DB의 {@code CHAR_LENGTH}와 같은 기준)
	 */
	public record Answer(
			@Schema(description = "질문의 id", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
			@NotNull Long introQuestionId,
			@Schema(description = "답변. 10 ~ 50자", example = "운동하고 나면 무조건 제로 콜라를 마셔요", requiredMode = Schema.RequiredMode.REQUIRED)
			@NotNull @CodePointLength(min = 10, max = 50, message = "10자 이상 50자 이하로 써야 합니다.") String answer) {
	}

}
