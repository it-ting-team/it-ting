package com.itting.notification.domain;

import java.time.Instant;

import com.itting.global.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 받는 사람 한 명에게 간 알림 하나({@code notification}). 지우지 않고 읽음으로만 바꾼다. 30일이 지나면 정리 작업이 지운다.
 *
 * <p>새 메시지 알림은 안 읽은 동안 같은 대화방의 새 메시지마다 {@code created_at} · {@code actor_id}를 바꾼다.
 * {@code created_at}은 엔티티로 바꿀 수 없으므로 저장소의 쿼리가 바꾼다.
 */
@Entity
@Table(name = "notification")
public class Notification extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false)
	private NotificationTargetType targetType;

	@Column(name = "target_id")
	private Long targetId;

	/** 알림을 일으킨 사람. 일으킨 사람이 없거나 드러내지 않는 알림은 비어 있다. */
	@Column(name = "actor_id")
	private Long actorId;

	/** 읽은 시각. 비어 있으면 안 읽은 알림이다. */
	@Column(name = "read_at")
	private Instant readAt;

	/** 같은 사건을 두 번 만들지 않게 하는 키. 받는 사람마다 겹치지 않는다. */
	@Column(name = "event_key", length = 191)
	private String eventKey;

	protected Notification() {
	}

	/**
	 * 일으킨 사람을 드러내지 않는 종류({@link NotificationType#actorShown()}가 거짓)는 {@code actorId}를 저장하지 않는다.
	 */
	public static Notification create(Long userId, NotificationType type, Long actorId, Long targetId,
			String eventKey) {
		Notification notification = new Notification();
		notification.userId = userId;
		notification.type = type;
		notification.targetType = type.targetType();
		notification.actorId = type.actorShown() ? actorId : null;
		notification.targetId = targetId;
		notification.eventKey = eventKey;
		return notification;
	}

	/**
	 * 읽음으로 바꾼다. 이미 읽었으면 그대로 두고 거짓을 돌려준다. 새 메시지 알림은 키를 비워, 그 대화방에 다음 메시지가 오면
	 * 새 줄이 생기게 한다.
	 */
	public boolean markRead(Instant now) {
		if (readAt != null) {
			return false;
		}
		readAt = now;
		if (type == NotificationType.CHAT_MESSAGE_RECEIVED) {
			eventKey = null;
		}
		return true;
	}

	public boolean isRead() {
		return readAt != null;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public NotificationType getType() {
		return type;
	}

	public NotificationTargetType getTargetType() {
		return targetType;
	}

	public Long getTargetId() {
		return targetId;
	}

	public Long getActorId() {
		return actorId;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public String getEventKey() {
		return eventKey;
	}

}
