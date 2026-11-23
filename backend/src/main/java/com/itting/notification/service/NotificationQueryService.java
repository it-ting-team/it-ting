package com.itting.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.itting.global.error.BusinessException;
import com.itting.notification.domain.Notification;
import com.itting.notification.domain.NotificationCategory;
import com.itting.notification.domain.NotificationErrorCode;
import com.itting.notification.domain.NotificationSetting;
import com.itting.notification.domain.NotificationTargetType;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.dto.NotificationItemResponse;
import com.itting.notification.dto.NotificationListResponse;
import com.itting.notification.dto.NotificationPopupResponse;
import com.itting.notification.dto.NotificationUnreadCountResponse;
import com.itting.notification.repository.NotificationRepository;
import com.itting.notification.repository.NotificationSettingRepository;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.UserDisplayService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 API(팝업, 목록, 안 읽은 수, 읽음, 모두 읽음). 모두 내가 받은 알림만 다루고, 30일 안의 알림만 본다.
 */
@Service
@Transactional(readOnly = true)
public class NotificationQueryService {

	/** 알림을 보여 주는 기간. 지난 알림은 어느 API도 주지 않고 {@link NotificationCleanupJob}이 지운다. */
	public static final Duration RETENTION = Duration.ofDays(30);

	static final int POPUP_SIZE = 5;

	private static final Set<NotificationType> ALL_TYPES = EnumSet.allOf(NotificationType.class);

	private final NotificationRepository notificationRepository;

	private final NotificationSettingRepository notificationSettingRepository;

	private final UserDisplayService userDisplayService;

	private final NotificationEventPublisher events;

	private final ObjectProvider<MeetupRequestCountReader> meetupRequestCountReader;

	private final ObjectProvider<ChatRequestReader> chatRequestReader;

	private final Map<NotificationTargetType, NotificationTargetReader> targetReaders;

	private final Clock clock;

	public NotificationQueryService(NotificationRepository notificationRepository,
			NotificationSettingRepository notificationSettingRepository, UserDisplayService userDisplayService,
			NotificationEventPublisher events, ObjectProvider<MeetupRequestCountReader> meetupRequestCountReader,
			ObjectProvider<ChatRequestReader> chatRequestReader,
			ObjectProvider<NotificationTargetReader> targetReaders, Clock clock) {
		this.notificationRepository = notificationRepository;
		this.notificationSettingRepository = notificationSettingRepository;
		this.userDisplayService = userDisplayService;
		this.events = events;
		this.meetupRequestCountReader = meetupRequestCountReader;
		this.chatRequestReader = chatRequestReader;
		this.targetReaders = new EnumMap<>(NotificationTargetType.class);
		targetReaders.orderedStream().forEach(reader -> {
			if (this.targetReaders.put(reader.targetType(), reader) != null) {
				throw new IllegalStateException("갈 곳의 종류마다 NotificationTargetReader는 하나여야 합니다: "
						+ reader.targetType());
			}
		});
		this.clock = clock;
	}

	/** {@code GET /api/v1/me/notifications/popup}. */
	public NotificationPopupResponse popup(Long userId) {
		Instant now = now();
		List<Notification> recent = notificationRepository.findRecent(userId, now.minus(RETENTION),
				NotificationType.REQUESTS, Limit.of(POPUP_SIZE));
		MeetupRequestCountReader meetupReader = meetupRequestCountReader.getIfAvailable();
		ChatRequestReader chatReader = chatRequestReader.getIfAvailable();
		long meetupRequestCount = meetupReader == null ? 0 : meetupReader.countPendingApplications(userId);
		long chatRequestCount = chatReader == null ? 0 : chatReader.countReceivedPending(userId);
		Long latestRequesterId = chatReader == null ? null : chatReader.findLatestRequesterId(userId).orElse(null);

		Set<Long> userIds = actorIds(recent);
		if (latestRequesterId != null) {
			userIds.add(latestRequesterId);
		}
		Map<Long, UserDisplayResponse> displays = userDisplayService.getAll(userIds);
		return new NotificationPopupResponse(meetupRequestCount, chatRequestCount,
				latestRequesterId == null ? null : displays.get(latestRequesterId), toItems(recent, displays),
				notificationRepository.countByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(userId,
						now.minus(RETENTION)));
	}

	/**
	 * {@code GET /api/v1/me/notifications}. 안 읽은 구간을 먼저 새것부터 주고, 다 주면 읽은 구간을 이어 준다.
	 *
	 * @param category 갈래. null이면 전체
	 * @param cursor 앞 응답의 {@code nextCursor}. 처음이면 null
	 */
	public NotificationListResponse list(Long userId, NotificationCategory category, int size, String cursor) {
		NotificationCursor position = cursor == null ? null : NotificationCursor.decode(cursor);
		Instant asOf = position == null ? now() : position.asOf();
		Instant from = asOf.minus(RETENTION);
		Set<NotificationType> types = category == null ? ALL_TYPES : NotificationType.of(Set.of(category));
		// 시각이 asOf 이하인 알림은 모두 이 자리보다 앞이다.
		Instant start = asOf.plus(1, ChronoUnit.MICROS);
		int wanted = size + 1;

		List<Notification> page = new ArrayList<>(wanted);
		List<Boolean> unreadFlags = new ArrayList<>(wanted);
		Instant readBeforeCreatedAt = start;
		long readBeforeId = Long.MAX_VALUE;
		if (position == null || position.unread()) {
			Instant beforeCreatedAt = position == null ? start : position.createdAt();
			long beforeId = position == null ? Long.MAX_VALUE : position.id();
			for (Notification notification : notificationRepository.findUnreadPage(userId, types, from, asOf,
					beforeCreatedAt, beforeId, Limit.of(wanted))) {
				page.add(notification);
				unreadFlags.add(true);
			}
		}
		else {
			readBeforeCreatedAt = position.createdAt();
			readBeforeId = position.id();
		}
		if (page.size() < wanted) {
			for (Notification notification : notificationRepository.findReadPage(userId, types, from, asOf,
					readBeforeCreatedAt, readBeforeId, Limit.of(wanted - page.size()))) {
				page.add(notification);
				unreadFlags.add(false);
			}
		}

		boolean hasNext = page.size() > size;
		List<Notification> shown = hasNext ? page.subList(0, size) : page;
		String nextCursor = null;
		if (hasNext) {
			Notification last = shown.getLast();
			nextCursor = new NotificationCursor(asOf, unreadFlags.get(size - 1), last.getCreatedAt(), last.getId())
					.encode();
		}
		Map<Long, UserDisplayResponse> displays = userDisplayService.getAll(actorIds(shown));
		return new NotificationListResponse(toItems(shown, displays), nextCursor, hasNext);
	}

	/** {@code GET /api/v1/me/notifications/unread-count}. 실시간 소식의 값도 이것으로 계산한다. */
	public NotificationUnreadCountResponse unreadCount(Long userId) {
		Instant from = now().minus(RETENTION);
		long unreadCount = notificationRepository.countByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(userId,
				from);
		if (unreadCount == 0) {
			return new NotificationUnreadCountResponse(0, false);
		}
		Set<NotificationCategory> enabled = notificationSettingRepository.findById(userId)
				.map(NotificationSetting::enabledCategories)
				.orElse(NotificationSetting.ALL_ON);
		boolean hasUnread = !enabled.isEmpty() && notificationRepository
				.existsByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqualAndTypeIn(userId, from,
						NotificationType.of(enabled));
		return new NotificationUnreadCountResponse(unreadCount, hasUnread);
	}

	/**
	 * {@code POST /api/v1/notifications/{notificationId}/read}. 이미 읽은 알림이면 바꾸지 않는다.
	 *
	 * @throws BusinessException 없거나, 남의 알림이거나, 30일이 지났으면 {@code 404 NOTIFICATION_NOT_FOUND}
	 */
	@Transactional
	public NotificationUnreadCountResponse read(Long userId, Long notificationId) {
		Instant now = now();
		Notification notification = notificationRepository
				.findByIdAndUserIdAndCreatedAtGreaterThanEqual(notificationId, userId, now.minus(RETENTION))
				.orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
		if (notification.markRead(now)) {
			notificationRepository.flush();
			events.unreadChanged(userId);
		}
		return unreadCount(userId);
	}

	/** {@code POST /api/v1/me/notifications/read-all}. 고른 탭과 상관없이 모든 갈래의 알림을 읽음으로 바꾼다. */
	@Transactional
	public NotificationUnreadCountResponse readAll(Long userId) {
		int changed = notificationRepository.markAllRead(userId, now(), NotificationType.CHAT_MESSAGE_RECEIVED);
		if (changed > 0) {
			events.unreadChanged(userId);
		}
		return new NotificationUnreadCountResponse(0, false);
	}

	private List<NotificationItemResponse> toItems(List<Notification> notifications,
			Map<Long, UserDisplayResponse> displays) {
		Map<NotificationTargetType, Map<Long, NotificationTargetSummary>> targets = readTargets(notifications);
		return notifications.stream().map(notification -> {
			NotificationTargetSummary summary = notification.getTargetId() == null ? null
					: targets.getOrDefault(notification.getTargetType(), Map.of()).get(notification.getTargetId());
			UserDisplayResponse actor = notification.getActorId() == null ? null
					: displays.get(notification.getActorId());
			return new NotificationItemResponse(notification.getId(), notification.getType(),
					notification.getType().category(), actor,
					new NotificationItemResponse.TargetResponse(notification.getTargetType(),
							notification.getTargetId(), summary == null ? null : summary.title(),
							summary == null ? null : summary.startsAt()),
					notification.isRead(), notification.getCreatedAt());
		}).toList();
	}

	/** 갈 곳의 종류마다 한 번씩 이름과 시작 시각을 읽는다. 구현이 없는 종류는 비어 있다. */
	private Map<NotificationTargetType, Map<Long, NotificationTargetSummary>> readTargets(
			List<Notification> notifications) {
		Map<NotificationTargetType, Set<Long>> idsByType = new EnumMap<>(NotificationTargetType.class);
		for (Notification notification : notifications) {
			if (notification.getTargetId() != null && targetReaders.containsKey(notification.getTargetType())) {
				idsByType.computeIfAbsent(notification.getTargetType(), type -> new HashSet<>())
						.add(notification.getTargetId());
			}
		}
		Map<NotificationTargetType, Map<Long, NotificationTargetSummary>> targets = new HashMap<>();
		idsByType.forEach((type, ids) -> targets.put(type, targetReaders.get(type).read(ids)));
		return targets;
	}

	private static Set<Long> actorIds(Collection<Notification> notifications) {
		return notifications.stream()
				.map(Notification::getActorId)
				.filter(Objects::nonNull)
				.collect(Collectors.toCollection(HashSet::new));
	}

	private Instant now() {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

}
