package com.itting.notice.service;

import java.util.List;

import com.itting.global.error.BusinessException;
import com.itting.notice.domain.Notice;
import com.itting.notice.domain.NoticeErrorCode;
import com.itting.notice.dto.NoticeListResponse;
import com.itting.notice.dto.NoticeListResponse.NoticeItemResponse;
import com.itting.notice.dto.NoticeResponse;
import com.itting.notice.repository.NoticeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지를 읽는다. 로그인한 누구나 같은 공지를 본다. 공지를 올리고 고치는 일은 11 운영 · 관리자에서 한다.
 */
@Service
@Transactional(readOnly = true)
public class NoticeService {

	/** 최신순. 올린 시각이 같으면 id가 큰 것이 먼저다. */
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final NoticeRepository noticeRepository;

	public NoticeService(NoticeRepository noticeRepository) {
		this.noticeRepository = noticeRepository;
	}

	/**
	 * 삭제하지 않은 공지를 최신순으로 한 페이지 준다. 본문은 담지 않는다. 공지가 있는 페이지를 넘으면 빈 목록이다.
	 *
	 * @param page 1부터 센다
	 */
	public NoticeListResponse getNotices(int page, int size) {
		Page<Notice> notices = noticeRepository.findByDeletedAtIsNull(PageRequest.of(page - 1, size, NEWEST_FIRST));
		List<NoticeItemResponse> items = notices.stream()
				.map(notice -> new NoticeItemResponse(notice.getId(), notice.getCategory(), notice.getTitle(),
						notice.getCreatedAt()))
				.toList();
		return new NoticeListResponse(items, page, size, notices.getTotalElements(), notices.getTotalPages());
	}

	/**
	 * 공지 한 건을 본문까지 준다.
	 *
	 * @throws BusinessException 없거나 삭제한 공지면 {@code NOTICE_NOT_FOUND}
	 */
	public NoticeResponse getNotice(Long noticeId) {
		Notice notice = noticeRepository.findByIdAndDeletedAtIsNull(noticeId)
				.orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));
		return new NoticeResponse(notice.getId(), notice.getCategory(), notice.getTitle(), notice.getContent(),
				notice.getCreatedAt());
	}

}
