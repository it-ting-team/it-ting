package com.itting.guestbook.controller;

import com.itting.global.response.ApiResult;
import com.itting.guestbook.dto.GuestbookEntryListResponse;
import com.itting.guestbook.service.GuestbookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "04 내 땅굴", description = "내 땅굴 조회, 두더지 · 내 정보 저장, 방명록 목록 · 삭제")
@RestController
@RequestMapping("/api/v1")
public class GuestbookController {

	/** 한 페이지의 글 수의 최댓값. 화면은 5개씩 쓴다(KAN-45 계획 A). */
	static final int MAX_SIZE = 20;

	private final GuestbookService guestbookService;

	public GuestbookController(GuestbookService guestbookService) {
		this.guestbookService = guestbookService;
	}

	@Operation(summary = "방명록 목록",
			description = "땅굴에 달린 방명록 글을 최신순으로 한 페이지 준다. 신고로 가려진 글은 빼고, 글이 있는 페이지를 넘으면 빈 목록이다. "
					+ "땅굴 주인과 방문자가 함께 쓰고, 글마다 canDelete · canReport를 요청한 사람 기준으로 준다. 로그인이 필요하다.")
	@GetMapping("/burrows/{burrowId}/guestbook-entries")
	public ApiResult<GuestbookEntryListResponse> getEntries(@PathVariable Long burrowId,
			@Parameter(description = "페이지 번호. 1부터") @RequestParam(defaultValue = "1")
			@Min(value = 1, message = "1 이상이어야 합니다.") int page,
			@Parameter(description = "한 페이지의 글 수. 1 ~ 20") @RequestParam(defaultValue = "5")
			@Min(value = 1, message = "1 이상 20 이하여야 합니다.")
			@Max(value = MAX_SIZE, message = "1 이상 20 이하여야 합니다.") int size,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return ApiResult.ok(guestbookService.getEntries(burrowId, userId, page, size));
	}

	@Operation(summary = "방명록 글 삭제",
			description = "방명록 글을 지운다. 땅굴 주인은 자기 땅굴에 달린 모든 글을, 쓴 사람은 자기 글을 지울 수 있다. "
					+ "되돌릴 수 없고 쓴 사람에게 알리지 않는다. 로그인이 필요하다.")
	@DeleteMapping("/guestbook-entries/{guestbookEntryId}")
	public ApiResult<Void> deleteEntry(@PathVariable Long guestbookEntryId,
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		guestbookService.deleteEntry(guestbookEntryId, userId);
		return ApiResult.ok();
	}

}
