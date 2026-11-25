package com.itting.community.dto;

import com.itting.community.domain.ReportReason;
import com.itting.community.domain.ReportRules;
import com.itting.community.domain.ReportTargetType;
import com.itting.global.validation.CodePointLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * {@code POST /api/v1/reports}의 본문. {@code detail}이 사유와 맞는지(기타면 꼭, 그 밖에는 null)는 서비스가 본다.
 */
public record ReportCreateRequest(
		@Schema(description = "POST(글), COMMENT(댓글이나 답글), MEMBER(사용자), GUESTBOOK(방명록 글) 가운데 하나",
				example = "COMMENT") @NotNull ReportTargetType targetType,
		@Schema(description = "대상의 id", example = "903") @NotNull Long targetId,
		@Schema(description = "SPAM, ABUSE, OBSCENE, ETC 가운데 하나", example = "ABUSE") @NotNull ReportReason reason,
		@Schema(description = "기타 사유. reason이 ETC면 1 ~ 100자, 그 밖에는 null")
		@CodePointLength(max = ReportRules.DETAIL_MAX_LENGTH, message = "100자 이하로 써야 합니다.") String detail) {
}
