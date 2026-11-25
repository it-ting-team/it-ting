package com.itting.guestbook.service;

import java.util.Optional;

import com.itting.burrow.domain.Burrow;
import com.itting.burrow.repository.BurrowRepository;
import com.itting.guestbook.domain.GuestbookEntry;
import com.itting.guestbook.repository.GuestbookEntryRepository;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고(08 커뮤니티의 {@code POST /api/v1/reports})가 방명록 글을 읽고 가릴 때 쓴다. 방명록 글의 상태는 이 영역의 코드로만 바꾼다.
 * 신고 API가 이 영역의 데이터를 바꾸는 까닭은 {@code docs/api/08-community.md}의 신고 절에 있다.
 *
 * <p>모두 신고를 받는 트랜잭션 안에서 부른다.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class GuestbookReportService {

	private final GuestbookEntryRepository guestbookEntryRepository;

	private final BurrowRepository burrowRepository;

	private final NotificationService notificationService;

	public GuestbookReportService(GuestbookEntryRepository guestbookEntryRepository,
			BurrowRepository burrowRepository, NotificationService notificationService) {
		this.guestbookEntryRepository = guestbookEntryRepository;
		this.burrowRepository = burrowRepository;
		this.notificationService = notificationService;
	}

	/**
	 * 신고할 수 있는 방명록 글.
	 *
	 * @param authorId 글을 쓴 사람
	 */
	public record ReportableEntry(Long id, Long authorId) {
	}

	/**
	 * 방명록 글을 트랜잭션이 끝날 때까지 잠그고 준다. 같은 글에 들어온 신고가 차례로 세어진다.
	 *
	 * @return 없거나(지운 글은 행이 없다) 이미 가려진 글이면 비어 있다
	 */
	public Optional<ReportableEntry> lockReportable(Long guestbookEntryId) {
		return guestbookEntryRepository.findByIdForUpdate(guestbookEntryId)
				.filter(entry -> !entry.isHidden())
				.map(entry -> new ReportableEntry(entry.getId(), entry.getAuthorId()));
	}

	/**
	 * 신고가 쌓인 방명록 글을 가리고, 쓴 사람에게 알린다. 알림은 그 땅굴로 가고 신고한 사람은 담지 않는다.
	 * {@link #lockReportable}로 잠근 공개 글에만 부른다.
	 */
	public void hideByReports(Long guestbookEntryId) {
		GuestbookEntry entry = guestbookEntryRepository.findById(guestbookEntryId).orElseThrow();
		entry.hide();
		Long burrowOwnerId = burrowRepository.findById(entry.getBurrowId())
				.map(Burrow::getUserId)
				.orElseThrow();
		notificationService.notify(entry.getAuthorId(), NotificationType.GUESTBOOK_ENTRY_HIDDEN, null, burrowOwnerId);
	}

}
