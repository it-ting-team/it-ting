package com.itting.chat.dto;

import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/v1/chat-requests}의 본문.
 *
 * @param receiverId 요청을 받을 사람의 계정 id. 나 자신이면 {@code 400 INVALID_INPUT}이다
 * @param message 짧은 인사. 보내지 않거나 null이나 빈 문자열이면 인사 없이 보낸다
 */
public record ChatRequestCreateRequest(
		@Schema(description = "요청을 받을 사람의 계정 id. 남의 땅굴 조회의 owner.id", example = "52",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "필수 값입니다.") Long receiverId,
		@Schema(description = "짧은 인사. 50자 이하. 보내지 않거나 null이나 빈 문자열이면 인사 없이 보낸다",
				example = "저도 러닝 좋아해요. 같이 달려요!")
		@CodePointLength(max = ChatRequestCreateRequest.MESSAGE_MAX, message = "50자 이하여야 합니다.") String message) {

	/** 인사의 최대 글자 수(유니코드 문자 단위). */
	public static final int MESSAGE_MAX = 50;

}
