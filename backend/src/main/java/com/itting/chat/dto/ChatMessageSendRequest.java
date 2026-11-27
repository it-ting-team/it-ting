package com.itting.chat.dto;

import java.util.List;

import com.itting.chat.domain.ChatRules;
import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * {@code POST /api/v1/chat-rooms/{chatRoomId}/messages}의 본문. 글만, 사진만, 또는 둘 다 보낸다. 글도 사진도 없으면
 * 서비스가 {@code 400 INVALID_INPUT}으로 답한다(쓸 수 없는 사진도 서비스가 본다).
 *
 * @param content 본문. 사진만 보낼 때는 null
 * @param imageIds 붙일 사진의 id. 보낸 순서로 보여 준다. 보내지 않으면 사진 없음
 */
public record ChatMessageSendRequest(
		@Schema(description = "본문. 1,000자 이하. 사진이 없으면 꼭 보내고 공백만으로는 안 된다. 사진만 보낼 때는 null",
				example = "좋아요. 7시에 여의나루역에서 봐요!")
		@CodePointLength(max = ChatRules.MESSAGE_MAX, message = "1,000자 이하여야 합니다.") String content,
		@Schema(description = "붙일 사진의 id. 0 ~ 3개. 사진 올리기(POST /api/v1/images)로 받은 id", example = "[]")
		@Size(max = ChatRules.IMAGE_MAX, message = "3장 이하로 붙여야 합니다.")
		@UniqueElements(message = "같은 사진을 두 번 붙일 수 없습니다.")
		List<@NotNull(message = "사진의 id가 비어 있습니다.") Long> imageIds) {

	/** 붙일 사진. 보내지 않았으면 빈 목록. */
	public List<Long> images() {
		return imageIds == null ? List.of() : imageIds;
	}

}
