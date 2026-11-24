package com.itting.guestbook.dto;

import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GuestbookEntryCreateRequest(
		@NotBlank @CodePointLength(max = 50, message = "50자 이하여야 합니다.")
		@Schema(description = "방명록 본문. 1 ~ 50자", example = "가구 구경 잘 하고 가요!") String content) {
}
