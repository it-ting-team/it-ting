package com.itting.guestbook.service;

import java.util.List;
import java.util.Map;

import com.itting.burrow.domain.Burrow;
import com.itting.burrow.domain.BurrowErrorCode;
import com.itting.burrow.repository.BurrowRepository;
import com.itting.burrow.service.BurrowService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.guestbook.domain.GuestbookEntry;
import com.itting.guestbook.domain.GuestbookEntryStatus;
import com.itting.guestbook.domain.GuestbookErrorCode;
import com.itting.guestbook.dto.GuestbookEntryListResponse;
import com.itting.guestbook.dto.GuestbookEntryListResponse.GuestbookEntryResponse;
import com.itting.guestbook.repository.GuestbookEntryRepository;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.UserDisplayService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 땅굴 방문자의 방명록 작성과 방명록 조회 · 삭제를 처리한다.
 */
@Service
@Transactional(readOnly = true)
public class GuestbookService {

	/** 최신순. 쓴 시각이 같으면 id가 큰 것이 먼저다. */
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final GuestbookEntryRepository guestbookEntryRepository;

	private final BurrowRepository burrowRepository;

	private final UserDisplayService userDisplayService;
	private final BurrowService burrowService;
	private final NotificationService notificationService;

	public GuestbookService(GuestbookEntryRepository guestbookEntryRepository, BurrowRepository burrowRepository,
			UserDisplayService userDisplayService, BurrowService burrowService, NotificationService notificationService) {
		this.guestbookEntryRepository = guestbookEntryRepository;
		this.burrowRepository = burrowRepository;
		this.userDisplayService = userDisplayService;
		this.burrowService = burrowService;
		this.notificationService = notificationService;
	}

	@Transactional
	public GuestbookEntryResponse createEntry(Long burrowId, Long userId, String content) {
		Burrow burrow = burrowService.lockForInteraction(burrowId, userId);
		if (burrow.getUserId().equals(userId)) {
			throw new BusinessException(BurrowErrorCode.OWN_BURROW_NOT_ALLOWED);
		}
		GuestbookEntry entry = guestbookEntryRepository.save(GuestbookEntry.create(burrowId, userId, content));
		notificationService.notify(burrow.getUserId(), NotificationType.GUESTBOOK_ENTRY_CREATED, userId, null);
		return new GuestbookEntryResponse(entry.getId(), userDisplayService.get(userId),
				entry.getContent(), entry.getCreatedAt(), true, false);
	}

	/**
	 * 땅굴에 달린 글 가운데 가려지지 않은 글을 최신순으로 한 페이지 준다. 글이 있는 페이지를 넘으면 빈 목록이다.
	 *
	 * @param viewerId 요청한 사람. 글마다 지울 수 있는지 · 신고할 수 있는지를 이 사람 기준으로 정한다
	 * @param page 1부터 센다
	 * @throws BusinessException 없는 땅굴이면 {@code BURROW_NOT_FOUND}
	 */
	public GuestbookEntryListResponse getEntries(Long burrowId, Long viewerId, int page, int size) {
		Burrow burrow = findBurrow(burrowId);
		Page<GuestbookEntry> entries = guestbookEntryRepository.findByBurrowIdAndStatus(burrowId,
				GuestbookEntryStatus.PUBLIC, PageRequest.of(page - 1, size, NEWEST_FIRST));
		Map<Long, UserDisplayResponse> authors = userDisplayService
				.getAll(entries.stream().map(GuestbookEntry::getAuthorId).distinct().toList());
		boolean isOwner = burrow.getUserId().equals(viewerId);

		List<GuestbookEntryResponse> items = entries.stream()
				.map(entry -> {
					boolean isAuthor = entry.getAuthorId().equals(viewerId);
					return new GuestbookEntryResponse(entry.getId(), authors.get(entry.getAuthorId()),
							entry.getContent(), entry.getCreatedAt(), isOwner || isAuthor, !isAuthor);
				})
				.toList();
		return new GuestbookEntryListResponse(items, page, size, entries.getTotalElements(), entries.getTotalPages());
	}

	/**
	 * 방명록 글을 지운다. 땅굴 주인은 자기 땅굴에 달린 모든 글을, 쓴 사람은 자기 글을 지울 수 있다. 되돌릴 수 없고 쓴 사람에게 알리지 않는다.
	 *
	 * @throws BusinessException 없거나 신고로 가려진 글이면 {@code GUESTBOOK_ENTRY_NOT_FOUND}, 주인도 쓴 사람도 아니면
	 *         {@code FORBIDDEN}
	 */
	@Transactional
	public void deleteEntry(Long guestbookEntryId, Long userId) {
		GuestbookEntry entry = guestbookEntryRepository.findById(guestbookEntryId)
				.filter(found -> !found.isHidden())
				.orElseThrow(() -> new BusinessException(GuestbookErrorCode.GUESTBOOK_ENTRY_NOT_FOUND));
		boolean isAuthor = entry.getAuthorId().equals(userId);
		boolean isOwner = burrowRepository.findById(entry.getBurrowId())
				.map(burrow -> burrow.getUserId().equals(userId))
				.orElse(false);
		if (!isOwner && !isAuthor) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN);
		}
		guestbookEntryRepository.delete(entry);
	}

	private Burrow findBurrow(Long burrowId) {
		return burrowRepository.findById(burrowId)
				.orElseThrow(() -> new BusinessException(BurrowErrorCode.BURROW_NOT_FOUND));
	}

}
