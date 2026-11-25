package com.itting.chat.domain;

import java.time.Duration;
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
 * 1:1 대화 요청({@code chat_request}). 두 사람 사이에 살아 있는 요청은 방향과 관계없이 하나뿐이다.
 *
 * <p>만료는 저장하지 않는다. 보낸 지 {@link #LIFETIME}이 지나면 상태와 관계없이 죽은 요청이다(정확히 그 시각이 되면 죽는다).
 * 살아 있는 요청은 보낸 지 5일이 지나지 않았고 {@link ChatRequestStatus#PENDING}이거나
 * {@link ChatRequestStatus#REJECTED}인 요청이다. 거절은 보낸 사람에게 드러나지 않아 {@code REJECTED}도 보낸 사람 쪽에서는
 * {@code PENDING}처럼 살아 있다.
 */
@Entity
@Table(name = "chat_request")
public class ChatRequest extends BaseCreatedTimeEntity {

	/** 요청이 살아 있는 기간. */
	public static final Duration LIFETIME = Duration.ofDays(5);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sender_id", nullable = false, updatable = false)
	private Long senderId;

	@Column(name = "receiver_id", nullable = false, updatable = false)
	private Long receiverId;

	/** 요청에 붙인 짧은 인사. 없으면 비어 있다. */
	@Column(length = 50, updatable = false)
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChatRequestStatus status;

	/** 수락해서 만든 1:1 대화방. 수락하기 전에는 비어 있다. */
	@Column(name = "chat_room_id")
	private Long chatRoomId;

	/** 수락 · 거절 · 취소 가운데 마지막 상태 변경 시각. */
	@Column(name = "responded_at")
	private Instant respondedAt;

	protected ChatRequest() {
	}

	/**
	 * 답을 기다리는 새 요청.
	 *
	 * @param message 인사. 없으면 null
	 */
	public static ChatRequest create(Long senderId, Long receiverId, String message) {
		ChatRequest request = new ChatRequest();
		request.senderId = senderId;
		request.receiverId = receiverId;
		request.message = message;
		request.status = ChatRequestStatus.PENDING;
		return request;
	}

	/** 지금 시각 기준으로, 이 시각보다 늦게 보낸 요청만 살아 있다. 저장소의 쿼리가 쓴다. */
	public static Instant aliveAfter(Instant now) {
		return now.minus(LIFETIME);
	}

	/** 요청이 만료되는 시각. */
	public Instant expiresAt() {
		return getCreatedAt().plus(LIFETIME);
	}

	/** 살아 있는 요청인가. 보낸 지 5일이 지나지 않았고 {@code PENDING}이거나 {@code REJECTED}다. */
	public boolean isAlive(Instant now) {
		return (status == ChatRequestStatus.PENDING || status == ChatRequestStatus.REJECTED)
				&& getCreatedAt().isAfter(aliveAfter(now));
	}

	/** 수락한다. 거절했던 요청을 거절한 사람이 새 요청을 보내면서 수락할 때도 쓴다. */
	public void accept(Long chatRoomId, Instant now) {
		this.status = ChatRequestStatus.ACCEPTED;
		this.chatRoomId = chatRoomId;
		this.respondedAt = now;
	}

	public void reject(Instant now) {
		this.status = ChatRequestStatus.REJECTED;
		this.respondedAt = now;
	}

	public void cancel(Instant now) {
		this.status = ChatRequestStatus.CANCELED;
		this.respondedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getSenderId() {
		return senderId;
	}

	public Long getReceiverId() {
		return receiverId;
	}

	public String getMessage() {
		return message;
	}

	public ChatRequestStatus getStatus() {
		return status;
	}

	public Long getChatRoomId() {
		return chatRoomId;
	}

	public Instant getRespondedAt() {
		return respondedAt;
	}

}
