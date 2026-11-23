package com.itting.user.dto;

import java.util.Optional;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.valueextraction.Unwrapping;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * {@code PATCH /api/v1/me/mole}의 본문. 네 부위의 키를 모두 보낸다. 바꾸지 않은 부위도 지금 값을 담는다.
 *
 * <p>키가 빠지면 null이 되어 {@code @NotNull}에 걸리고, {@code "head": null}("없음")은 빈 {@link Optional}이 된다
 * ({@link KeyRequiredStringDeserializer}). 아이템 코드는 모양만 본다.
 */
public record MoleUpdateRequest(
		@Schema(description = "머리 아이템 코드. \"없음\"이면 null", example = "CAP_RED", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED, implementation = String.class)
		@JsonDeserialize(using = KeyRequiredStringDeserializer.class)
		@NotNull(message = REQUIRED, payload = Unwrapping.Skip.class) Optional<@Pattern(regexp = ITEM_CODE, message = ITEM_CODE_MESSAGE) String> head,
		@Schema(description = "얼굴 아이템 코드. \"없음\"이면 null", example = "GLASSES_ROUND", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED, implementation = String.class)
		@JsonDeserialize(using = KeyRequiredStringDeserializer.class)
		@NotNull(message = REQUIRED, payload = Unwrapping.Skip.class) Optional<@Pattern(regexp = ITEM_CODE, message = ITEM_CODE_MESSAGE) String> face,
		@Schema(description = "목 아이템 코드. \"없음\"이면 null", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED, implementation = String.class)
		@JsonDeserialize(using = KeyRequiredStringDeserializer.class)
		@NotNull(message = REQUIRED, payload = Unwrapping.Skip.class) Optional<@Pattern(regexp = ITEM_CODE, message = ITEM_CODE_MESSAGE) String> neck,
		@Schema(description = "손 아이템 코드. \"없음\"이면 null", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED, implementation = String.class)
		@JsonDeserialize(using = KeyRequiredStringDeserializer.class)
		@NotNull(message = REQUIRED, payload = Unwrapping.Skip.class) Optional<@Pattern(regexp = ITEM_CODE, message = ITEM_CODE_MESSAGE) String> hand) {

	/** 온보딩과 같은 아이템 코드 모양: 영어 대문자 · 숫자 · 밑줄, 30자 이하. */
	public static final String ITEM_CODE = "[A-Z0-9_]{1,30}";

	public static final String ITEM_CODE_MESSAGE = "영어 대문자, 숫자, 밑줄로 30자 이하여야 합니다.";

	static final String REQUIRED = "필수 값입니다. 없으면 null을 보내세요.";

}
