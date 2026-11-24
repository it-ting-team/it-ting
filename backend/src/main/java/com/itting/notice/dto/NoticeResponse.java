package com.itting.notice.dto;

import java.time.Instant;

import com.itting.notice.domain.NoticeCategory;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/notices/{noticeId}}의 {@code data}.
 */
public record NoticeResponse(
		@Schema(description = "공지의 id", example = "12") Long id,
		@Schema(description = "분류. NOTICE(공지), UPDATE(업데이트), EVENT(이벤트)", example = "UPDATE") NoticeCategory category,
		@Schema(description = "제목", example = "모임 캘린더가 새로 생겼어요") String title,
		@Schema(description = "본문. 줄바꿈이 있는 글이다", example = "내 모임에서 캘린더를 열 수 있어요.\n참여하는 모임과 내가 연 모임이 날짜별로 보입니다.") String content,
		@Schema(description = "올린 시각", example = "2026-11-18T02:00:00Z") Instant createdAt) {
}
