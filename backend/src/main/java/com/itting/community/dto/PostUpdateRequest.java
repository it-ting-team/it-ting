package com.itting.community.dto;

import java.util.List;
import java.util.Optional;

import com.itting.community.domain.PostCategory;
import com.itting.community.domain.PostRules;
import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.valueextraction.Unwrapping;
import org.hibernate.validator.constraints.UniqueElements;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code PATCH /api/v1/posts/{postId}}의 본문. 바꾸지 않은 값도 지금 값을 담아 모두 보낸다.
 * 글을 찾고 권한을 본 뒤에 검사하므로 컨트롤러가 아니라 서비스가 검사한다.
 *
 * @param meetupId 키가 빠지면 {@code null}, {@code "meetupId": null}이면 빈 {@link Optional}이다.
 *        키가 빠지면 {@code 400}, 값이 null이면 모임을 연결하지 않는다
 */
public record PostUpdateRequest(
		@Schema(description = "PROMOTION, REVIEW, FREE 가운데 하나", example = "FREE") @NotNull PostCategory category,
		@Schema(description = "제목. 1 ~ 50자", example = "5층 라운지 커피머신 쓰는 법 (사진 추가)")
		@NotBlank(message = "공백만으로는 쓸 수 없습니다.")
		@CodePointLength(max = PostRules.TITLE_MAX, message = "50자 이하로 써야 합니다.") String title,
		@Schema(description = "본문. 1 ~ 1,000자", example = "물통을 먼저 채우고 전원을 켜면 됩니다.")
		@NotBlank(message = "공백만으로는 쓸 수 없습니다.")
		@CodePointLength(max = PostRules.CONTENT_MAX, message = "1,000자 이하로 써야 합니다.") String content,
		@Schema(description = "관심사 대분류의 id. 0 ~ 5개", example = "[3]")
		@NotNull @Size(max = PostRules.INTEREST_MAX, message = "5개 이하로 골라야 합니다.")
		@UniqueElements(message = "같은 관심사를 두 번 고를 수 없습니다.") List<@NotNull Long> interestCategoryIds,
		@Schema(description = "붙일 사진의 id. 0 ~ 3개. 그대로 둘 사진은 글 상세의 images[].id를 보낸다", example = "[]")
		@NotNull @Size(max = PostRules.IMAGE_MAX, message = "3장 이하로 붙여야 합니다.")
		@UniqueElements(message = "같은 사진을 두 번 붙일 수 없습니다.") List<@NotNull Long> imageIds,
		@Schema(description = "연결할 모임의 id. 연결하지 않으면 null. 키는 꼭 보낸다", type = "integer",
				format = "int64", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
		@JsonDeserialize(using = KeyRequiredLongDeserializer.class)
		@NotNull(message = "필수 값입니다.", payload = Unwrapping.Skip.class) Optional<Long> meetupId)
		implements PostInput {

	@Override
	public Long linkedMeetupId() {
		return meetupId == null ? null : meetupId.orElse(null);
	}

	/**
	 * 키가 빠지면 null, 값이 null이면 빈 {@link Optional}로 읽는다. 기본 {@code Optional} 읽기는 둘 다 빈 값으로 읽어
	 * 키가 빠진 것을 알 수 없다.
	 */
	static class KeyRequiredLongDeserializer extends ValueDeserializer<Optional<Long>> {

		@Override
		public Optional<Long> deserialize(JsonParser parser, DeserializationContext context) {
			return Optional.ofNullable(context.readValue(parser, Long.class));
		}

		@Override
		public Optional<Long> getNullValue(DeserializationContext context) {
			return Optional.empty();
		}

		@Override
		public Object getAbsentValue(DeserializationContext context) {
			return null;
		}

	}

}
