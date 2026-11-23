package com.itting.guestbook.dto;

import java.time.Instant;
import java.util.List;

import com.itting.user.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/burrows/{burrowId}/guestbook-entries}의 {@code data}. 페이지 방식이다.
 */
public record GuestbookEntryListResponse(
		@Schema(description = "방명록 글. 최신순") List<GuestbookEntryResponse> items,
		@Schema(description = "지금 페이지 번호. 1부터", example = "1") int page,
		@Schema(description = "한 페이지의 글 수", example = "5") int size,
		@Schema(description = "글 수. 신고로 가려진 글은 뺀다", example = "2") long totalItems,
		@Schema(description = "전체 페이지 수. 글이 없으면 0", example = "1") int totalPages) {

	public record GuestbookEntryResponse(
			@Schema(description = "방명록 글의 id", example = "204") Long id,
			@Schema(description = "쓴 사람의 사용자 표시. 탈퇴한 사람이면 isWithdrawn이 true") UserSummaryResponse author,
			@Schema(description = "본문. 50자 이하", example = "가구 구경 잘 하고 가요. 다음에 같이 달려요!") String content,
			@Schema(description = "쓴 시각", example = "2026-11-19T12:40:11Z") Instant createdAt,
			@Schema(description = "요청한 사람이 이 글을 지울 수 있는지. 땅굴 주인이거나 쓴 사람이면 true", example = "true") boolean canDelete,
			@Schema(description = "요청한 사람이 이 글을 신고할 수 있는지. 쓴 사람이 아니면 true", example = "true") boolean canReport) {
	}

}
