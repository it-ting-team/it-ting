package com.itting.chat.dto;

import com.itting.chat.domain.ChatRules;
import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code PUT /api/v1/chat-rooms/{chatRoomId}/notice}의 본문.
 *
 * @param content 공지의 본문. 받은 그대로 저장한다
 */
public record ChatNoticeRequest(
		@Schema(description = "공지의 본문. 1 ~ 200자. 공백만으로는 쓸 수 없다",
				example = "매주 토요일 오후 2시에 모여요. 노트북을 가져오세요.", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "공백만으로는 쓸 수 없습니다.")
		@CodePointLength(max = ChatRules.NOTICE_MAX, message = "200자 이하여야 합니다.") String content) {
}
