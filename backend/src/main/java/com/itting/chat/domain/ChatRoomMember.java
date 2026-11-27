package com.itting.chat.domain;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 대화방에 들어 있는 사람과 읽은 자리({@code chat_room_member}). {@code joined_at}이 만든 시각의 구실을 하므로
 * {@code created_at}은 없다. {@code left_at}이 채워지면 그 방을 나간 것이고 다시 들어올 수 없다.
 */
@Entity
@Table(name = "chat_room_member")
@IdClass(ChatRoomMember.Key.class)
public class ChatRoomMember {

	@Id
	@Column(name = "chat_room_id")
	private Long chatRoomId;

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	@Column(name = "left_at")
	private Instant leftAt;

	/** 마지막으로 읽은 같은 방의 메시지 id. {@code chat_message}를 가리키는 논리 참조라 FK는 없다. */
	@Column(name = "last_read_message_id")
	private Long lastReadMessageId;

	protected ChatRoomMember() {
	}

	/**
	 * 방에 들어온 사람.
	 *
	 * @param lastReadMessageId 들어올 때 이미 읽은 자리. 없으면 null
	 */
	public static ChatRoomMember join(Long chatRoomId, Long userId, Instant joinedAt, Long lastReadMessageId) {
		ChatRoomMember member = new ChatRoomMember();
		member.chatRoomId = chatRoomId;
		member.userId = userId;
		member.joinedAt = joinedAt;
		member.lastReadMessageId = lastReadMessageId;
		return member;
	}

	public Long getChatRoomId() {
		return chatRoomId;
	}

	public Long getUserId() {
		return userId;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

	public Instant getLeftAt() {
		return leftAt;
	}

	public Long getLastReadMessageId() {
		return lastReadMessageId;
	}

	public void leave(Instant now) {
		leftAt = now;
	}

	/** 아직 방에 있는지. 나갔으면 다시 들어올 수 없다. */
	public boolean isJoined() {
		return leftAt == null;
	}

	/** 복합 기본키 {@code (chat_room_id, user_id)}. */
	public record Key(Long chatRoomId, Long userId) implements Serializable {
	}

}
