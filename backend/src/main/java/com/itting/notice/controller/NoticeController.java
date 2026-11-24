package com.itting.notice.controller;

import com.itting.global.response.ApiResult;
import com.itting.notice.dto.NoticeListResponse;
import com.itting.notice.dto.NoticeResponse;
import com.itting.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10 설정", description = "계정 정보, 비밀번호 변경, 알림 설정, 공지사항")
@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

	/** 한 페이지의 공지 수의 최댓값. */
	static final int MAX_SIZE = 50;

	private final NoticeService noticeService;

	public NoticeController(NoticeService noticeService) {
		this.noticeService = noticeService;
	}

	@Operation(summary = "공지 목록",
			description = "삭제하지 않은 공지를 최신순(올린 시각이 같으면 id가 큰 것 먼저)으로 한 페이지 준다. 분류로 거르지 않고 본문은 담지 않는다. "
					+ "공지가 있는 페이지를 넘으면 빈 목록이다. 로그인이 필요하다.")
	@GetMapping
	public ApiResult<NoticeListResponse> getNotices(
			@Parameter(description = "페이지 번호. 1부터") @RequestParam(defaultValue = "1")
			@Min(value = 1, message = "1 이상이어야 합니다.") int page,
			@Parameter(description = "한 페이지의 공지 수. 1 ~ 50") @RequestParam(defaultValue = "10")
			@Min(value = 1, message = "1 이상 50 이하여야 합니다.")
			@Max(value = MAX_SIZE, message = "1 이상 50 이하여야 합니다.") int size) {
		return ApiResult.ok(noticeService.getNotices(page, size));
	}

	@Operation(summary = "공지 상세",
			description = "공지 한 건을 본문까지 준다. 없거나 삭제한 공지면 404 NOTICE_NOT_FOUND. 이전 글 · 다음 글은 주지 않는다. "
					+ "공지를 읽어도 공지 알림은 읽음으로 바뀌지 않는다. 로그인이 필요하다.")
	@GetMapping("/{noticeId}")
	public ApiResult<NoticeResponse> getNotice(@Parameter(description = "공지의 id") @PathVariable Long noticeId) {
		return ApiResult.ok(noticeService.getNotice(noticeId));
	}

}
