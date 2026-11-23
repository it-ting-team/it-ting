package com.itting.community.dto;

import java.util.List;

import com.itting.community.domain.PostCategory;
import com.itting.community.domain.PostRules;
import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * {@code POST /api/v1/posts}의 본문. 서비스가 DB와 비교하는 검사(없는 대분류, 쓸 수 없는 사진)와 함께 검사한다.
 *
 * @param interestCategoryIds 붙일 관심사 대분류의 id. 없으면 {@code []}
 * @param imageIds 붙일 사진의 id. 보낸 순서로 보여 주고 첫 장이 대표 사진이다. 없으면 {@code []}
 * @param meetupId 연결할 모임의 id. {@code PROMOTION}, {@code REVIEW}면 꼭 보낸다
 */
public record PostCreateRequest(
		@Schema(description = "PROMOTION, REVIEW, FREE 가운데 하나", example = "FREE") @NotNull PostCategory category,
		@Schema(description = "제목. 1 ~ 50자", example = "5층 라운지 커피머신 쓰는 법")
		@NotBlank(message = "공백만으로는 쓸 수 없습니다.")
		@CodePointLength(max = PostRules.TITLE_MAX, message = "50자 이하로 써야 합니다.") String title,
		@Schema(description = "본문. 1 ~ 1,000자", example = "물통을 먼저 채우고 전원을 켜면 됩니다.")
		@NotBlank(message = "공백만으로는 쓸 수 없습니다.")
		@CodePointLength(max = PostRules.CONTENT_MAX, message = "1,000자 이하로 써야 합니다.") String content,
		@Schema(description = "관심사 대분류의 id. 0 ~ 5개", example = "[3]")
		@NotNull @Size(max = PostRules.INTEREST_MAX, message = "5개 이하로 골라야 합니다.")
		@UniqueElements(message = "같은 관심사를 두 번 고를 수 없습니다.") List<@NotNull Long> interestCategoryIds,
		@Schema(description = "붙일 사진의 id. 0 ~ 3개", example = "[]")
		@NotNull @Size(max = PostRules.IMAGE_MAX, message = "3장 이하로 붙여야 합니다.")
		@UniqueElements(message = "같은 사진을 두 번 붙일 수 없습니다.") List<@NotNull Long> imageIds,
		@Schema(description = "연결할 모임의 id. 연결하지 않으면 null") Long meetupId) implements PostInput {

	@Override
	public Long linkedMeetupId() {
		return meetupId;
	}

}
