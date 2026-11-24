package com.itting.notice.dto;

import java.time.Instant;
import java.util.List;

import com.itting.notice.domain.NoticeCategory;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/notices}의 {@code data}. 페이지 방식이다. 본문은 담지 않는다.
 */
public record NoticeListResponse(
		@Schema(description = "공지. 최신순") List<NoticeItemResponse> items,
		@Schema(description = "지금 페이지 번호. 1부터", example = "1") int page,
		@Schema(description = "한 페이지의 공지 수", example = "10") int size,
		@Schema(description = "삭제하지 않은 공지의 수", example = "3") long totalItems,
		@Schema(description = "전체 페이지 수. 공지가 없으면 0", example = "1") int totalPages) {

	public record NoticeItemResponse(
			@Schema(description = "공지의 id", example = "12") Long id,
			@Schema(description = "분류. NOTICE(공지), UPDATE(업데이트), EVENT(이벤트)", example = "UPDATE") NoticeCategory category,
			@Schema(description = "제목", example = "모임 캘린더가 새로 생겼어요") String title,
			@Schema(description = "올린 시각", example = "2026-11-18T02:00:00Z") Instant createdAt) {
	}

}
