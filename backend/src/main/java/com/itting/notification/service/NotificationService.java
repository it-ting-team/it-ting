package com.itting.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.itting.notification.domain.Notification;
import com.itting.notification.domain.NotificationSetting;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.repository.NotificationRepository;
import com.itting.notification.repository.NotificationSettingRepository;
import com.itting.user.domain.User;
import com.itting.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 영역(모임, 채팅, 커뮤니티, 땅굴)과 예약 작업이 알림을 만들 때 부른다({@code docs/api/09-notification.md}의
 * "알림을 만들 때의 규칙").
 *
 * <p>모든 메서드는 부른 쪽의 트랜잭션에 함께 들어간다. 부른 쪽의 일이 되돌려지면 알림도 남지 않는다.
 * 아래 경우에는 알림을 만들지 않고 조용히 넘어간다.
 * <ul>
 * <li>받는 사람이 그 갈래의 알림을 꺼 두었다({@code notification_setting})
 * <li>받는 사람이 일으킨 사람 자신이다
 * <li>받는 사람이 탈퇴했거나 없는 계정이다
 * </ul>
 * 일으킨 사람을 드러내지 않는 종류는 {@code actorId}를 넘겨도 저장하지 않는다. 그래도 자기 자신을 빼는 데는 쓴다.
 * 알림이 생기면 커밋 뒤에 {@link NotificationCreatedEvent}와 {@link NotificationUnreadChangedEvent}를 낸다.
 */
@Service
@Transactional
public class NotificationService {

	private final NotificationRepository notificationRepository;

	private final NotificationSettingRepository notificationSettingRepository;

	private final UserRepository userRepository;

	private final NotificationEventPublisher events;

	private final Clock clock;

	public NotificationService(NotificationRepository notificationRepository,
			NotificationSettingRepository notificationSettingRepository, UserRepository userRepository,
			NotificationEventPublisher events, Clock clock) {
		this.notificationRepository = notificationRepository;
		this.notificationSettingRepository = notificationSettingRepository;
		this.userRepository = userRepository;
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 한 사람에게 알림을 만든다.
	 *
	 * @param receiverId 받는 사람
	 * @param type 알림의 종류. 갈 곳의 종류는 종류에서 정해진다
	 * @param actorId 일으킨 사람. 없으면 null
	 * @param targetId 갈 곳의 id. 갈 곳이 내 땅굴의 방명록({@code GUESTBOOK})이면 null
	 */
	public void notify(Long receiverId, NotificationType type, Long actorId, Long targetId) {
		notifyAll(List.of(receiverId), type, actorId, targetId);
	}

	/**
	 * 여러 사람에게 같은 알림을 만든다(모임 수정 · 취소처럼). 같은 사람이 여러 번 있어도 한 번만 만든다.
	 */
	public void notifyAll(Collection<Long> receiverIds, NotificationType type, Long actorId, Long targetId) {
		for (Long receiverId : receivers(receiverIds, type, actorId)) {
			Notification notification = notificationRepository
					.save(Notification.create(receiverId, type, actorId, targetId, null));
			events.created(notification.getId(), receiverId);
		}
	}

	/**
	 * 같은 사건으로 이미 만든 알림이 없을 때만 만든다. 예약 작업이 다시 돌아도 같은 알림이 두 번 생기지 않는다.
	 * 동시에 불려도 {@code UNIQUE(user_id, event_key)}로 한 줄만 생긴다.
	 *
	 * @param eventKey 같은 사건을 가리키는 키. 191자 이하. 일정 임박은
	 * {@code MEETUP_START_REMINDER:<meetupId>:<startsAt의 epoch 초>:<24H|1H>}
	 */
	public void notifyOnce(Long receiverId, NotificationType type, Long actorId, Long targetId, String eventKey) {
		if (eventKey == null || eventKey.isBlank() || eventKey.length() > 191) {
			throw new IllegalArgumentException("eventKey는 1 ~ 191자여야 합니다: " + eventKey);
		}
		if (receivers(List.of(receiverId), type, actorId).isEmpty()
				|| notificationRepository.findByUserIdAndEventKey(receiverId, eventKey).isPresent()) {
			return;
		}
		notificationRepository.insertIfAbsent(receiverId, type.name(), type.targetType().name(), targetId,
				type.actorShown() ? actorId : null, now(), eventKey);
		notificationRepository.findByUserIdAndEventKey(receiverId, eventKey)
				.ifPresent(notification -> events.created(notification.getId(), receiverId));
	}

	/**
	 * 대화방에 사람이 보낸 새 메시지의 알림. 받는 사람에게 그 대화방의 안 읽은 새 메시지 알림이 있으면 새로 만들지 않고 그 줄의
	 * 시각과 일으킨 사람을 이 메시지로 바꾼다. 안내 메시지("참여했어요" 등)로는 부르지 않는다.
	 *
	 * @param receiverId 받는 사람. 대화방에 있는 보낸 사람이 아닌 사람
	 * @param chatRoomId 대화방의 id
	 * @param senderId 메시지를 보낸 사람
	 */
	public void notifyChatMessage(Long receiverId, Long chatRoomId, Long senderId) {
		NotificationType type = NotificationType.CHAT_MESSAGE_RECEIVED;
		if (receivers(List.of(receiverId), type, senderId).isEmpty()) {
			return;
		}
		String eventKey = chatMessageKey(chatRoomId);
		notificationRepository.upsert(receiverId, type.name(), type.targetType().name(), chatRoomId, senderId, now(),
				eventKey);
		notificationRepository.findByUserIdAndEventKey(receiverId, eventKey)
				.ifPresent(notification -> events.created(notification.getId(), receiverId));
	}

	/**
	 * 받는 사람이 대화방을 읽었을 때, 그 대화방의 안 읽은 새 메시지 알림을 읽음으로 바꾼다. 06 채팅의 읽음 API가 부른다.
	 */
	public void readChatRoom(Long userId, Long chatRoomId) {
		int changed = notificationRepository.markChatRoomRead(userId, chatRoomId,
				NotificationType.CHAT_MESSAGE_RECEIVED, now());
		if (changed > 0) {
			events.unreadChanged(userId);
		}
	}

	/** 새 메시지 알림이 안 읽은 동안 대화방마다 한 줄이게 하는 키. 읽으면 비운다. */
	static String chatMessageKey(Long chatRoomId) {
		return NotificationType.CHAT_MESSAGE_RECEIVED.name() + ":" + chatRoomId;
	}

	/** 알림을 받을 사람. 자기 자신, 탈퇴한 사람, 그 갈래를 끈 사람을 뺀다. */
	private Set<Long> receivers(Collection<Long> receiverIds, NotificationType type, Long actorId) {
		Set<Long> candidates = receiverIds.stream()
				.filter(Objects::nonNull)
				.filter(id -> !id.equals(actorId))
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (candidates.isEmpty()) {
			return Set.of();
		}
		Set<Long> active = userRepository.findAllById(candidates).stream()
				.filter(User::isActive)
				.map(User::getId)
				.collect(Collectors.toSet());
		Map<Long, NotificationSetting> settings = notificationSettingRepository.findAllById(candidates).stream()
				.collect(Collectors.toMap(NotificationSetting::getUserId, Function.identity()));
		return candidates.stream()
				.filter(active::contains)
				.filter(id -> !settings.containsKey(id) || settings.get(id).isOn(type.category()))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private Instant now() {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

}
