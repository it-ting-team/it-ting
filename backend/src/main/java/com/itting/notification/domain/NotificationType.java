package com.itting.notification.domain;

import static com.itting.notification.domain.NotificationCategory.BURROW;
import static com.itting.notification.domain.NotificationCategory.CHAT;
import static com.itting.notification.domain.NotificationCategory.COMMUNITY;
import static com.itting.notification.domain.NotificationCategory.MEETUP;
import static com.itting.notification.domain.NotificationCategory.NOTICE;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 알림의 종류({@code notification.type}). 종류마다 갈래와 눌렀을 때 갈 곳의 종류가 하나로 정해져 있다.
 * 언제 누구에게 만드는지는 {@code docs/api/09-notification.md}의 "알림의 종류"에 있다.
 */
public enum NotificationType {

	MEETUP_APPLICATION_RECEIVED(MEETUP, NotificationTargetType.MEETUP, true),
	MEETUP_APPLICATION_ACCEPTED(MEETUP, NotificationTargetType.MEETUP, true),
	MEETUP_APPLICATION_REJECTED(MEETUP, NotificationTargetType.MEETUP, false),
	MEETUP_PARTICIPATION_CANCELED(MEETUP, NotificationTargetType.MEETUP, true),
	MEETUP_KICKED(MEETUP, NotificationTargetType.MEETUP, false),
	MEETUP_UPDATED(MEETUP, NotificationTargetType.MEETUP, false),
	MEETUP_CANCELED(MEETUP, NotificationTargetType.MEETUP, false),
	MEETUP_DELETED(MEETUP, NotificationTargetType.MEETUP, false),
	MEETUP_VACANCY_OPENED(MEETUP, NotificationTargetType.MEETUP, false),
	MEETUP_START_REMINDER(MEETUP, NotificationTargetType.MEETUP, false),
	CHAT_MESSAGE_RECEIVED(CHAT, NotificationTargetType.CHAT_ROOM, true),
	CHAT_REQUEST_RECEIVED(CHAT, NotificationTargetType.CHAT_REQUEST, true),
	CHAT_REQUEST_ACCEPTED(CHAT, NotificationTargetType.CHAT_ROOM, true),
	POST_COMMENTED(COMMUNITY, NotificationTargetType.POST, true),
	POST_LIKED(COMMUNITY, NotificationTargetType.POST, true),
	COMMENT_REPLIED(COMMUNITY, NotificationTargetType.POST, true),
	COMMENT_LIKED(COMMUNITY, NotificationTargetType.POST, true),
	POST_HIDDEN(COMMUNITY, NotificationTargetType.POST, false),
	COMMENT_HIDDEN(COMMUNITY, NotificationTargetType.POST, false),
	GUESTBOOK_ENTRY_CREATED(BURROW, NotificationTargetType.GUESTBOOK, true),
	GUESTBOOK_ENTRY_HIDDEN(BURROW, NotificationTargetType.BURROW, false),
	NOTICE_PUBLISHED(NOTICE, NotificationTargetType.NOTICE, false);

	/** 요청 알림. 알림 팝업의 최근 알림에서는 빼고, 팝업 맨 위의 요청 두 줄이 대신한다. */
	public static final Set<NotificationType> REQUESTS = EnumSet.of(MEETUP_APPLICATION_RECEIVED,
			CHAT_REQUEST_RECEIVED);

	private final NotificationCategory category;

	private final NotificationTargetType targetType;

	private final boolean actorShown;

	NotificationType(NotificationCategory category, NotificationTargetType targetType, boolean actorShown) {
		this.category = category;
		this.targetType = targetType;
		this.actorShown = actorShown;
	}

	public NotificationCategory category() {
		return category;
	}

	public NotificationTargetType targetType() {
		return targetType;
	}

	/**
	 * 일으킨 사람을 담는 종류인지. 거절 · 내보내기 · 모임 수정 · 취소 · 삭제, 신고로 가려짐, 서버가 만드는 알림은 담지 않는다
	 * (신고한 사람과 누가 했는지를 드러내지 않는다).
	 */
	public boolean actorShown() {
		return actorShown;
	}

	/** 이 갈래들에 드는 종류. */
	public static Set<NotificationType> of(Collection<NotificationCategory> categories) {
		Set<NotificationType> types = EnumSet.noneOf(NotificationType.class);
		Arrays.stream(values()).filter(type -> categories.contains(type.category)).forEach(types::add);
		return types;
	}

}
