package com.itting.notice.service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.itting.notice.domain.Notice;
import com.itting.notice.repository.NoticeRepository;
import com.itting.notification.domain.NotificationTargetType;
import com.itting.notification.service.NotificationTargetReader;
import com.itting.notification.service.NotificationTargetSummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 알림({@link NotificationTargetType#NOTICE})의 갈 곳 이름으로 공지 제목을 준다. 삭제한 공지는 결과에 담지 않는다.
 */
@Component
public class NoticeTargetReader implements NotificationTargetReader {

	private final NoticeRepository noticeRepository;

	public NoticeTargetReader(NoticeRepository noticeRepository) {
		this.noticeRepository = noticeRepository;
	}

	@Override
	public NotificationTargetType targetType() {
		return NotificationTargetType.NOTICE;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, NotificationTargetSummary> read(Collection<Long> targetIds) {
		return noticeRepository.findByIdInAndDeletedAtIsNull(targetIds).stream()
				.collect(Collectors.toMap(Notice::getId,
						notice -> new NotificationTargetSummary(notice.getTitle(), null)));
	}

}
