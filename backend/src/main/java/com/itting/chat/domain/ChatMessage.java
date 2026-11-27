package com.itting.chat.domain;

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
 * 대화방의 메시지와 안내 메시지({@code chat_message}). 고치거나 지우지 않는다. 같은 방 안에서 id가 클수록 나중 메시지다.
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage extends BaseCreatedTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "chat_room_id", nullable = false, updatable = false)
	private Long chatRoomId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private ChatMessageType type;

	/** 보낸 사람, 또는 안내를 일으킨 사람. 탈퇴해도 그대로다. */
	@Column(name = "sender_id", nullable = false, updatable = false)
	private Long senderId;

	/** 본문. 안내 메시지는 비어 있다. */
	@Column(columnDefinition = "TEXT", updatable = false)
	private String content;

	protected ChatMessage() {
	}

	/** 사람이 보낸 메시지. */
	public static ChatMessage text(Long chatRoomId, Long senderId, String content) {
		return create(chatRoomId, ChatMessageType.TEXT, senderId, content);
	}

	/** 대화 요청을 수락해 1:1 대화방이 열렸다는 안내. {@code accepterId}는 수락한 사람이다. */
	public static ChatMessage accepted(Long chatRoomId, Long accepterId) {
		return create(chatRoomId, ChatMessageType.ACCEPTED, accepterId, null);
	}

	public static ChatMessage joined(Long chatRoomId, Long userId) {
		return create(chatRoomId, ChatMessageType.JOINED, userId, null);
	}

	public static ChatMessage left(Long chatRoomId, Long userId) {
		return create(chatRoomId, ChatMessageType.LEFT, userId, null);
	}

	/** 회원 탈퇴로 대화방에서 나갔다는 안내. {@code userId}는 탈퇴한 사람이다. */
	public static ChatMessage withdrawn(Long chatRoomId, Long userId) {
		return create(chatRoomId, ChatMessageType.WITHDRAWN, userId, null);
	}

	private static ChatMessage create(Long chatRoomId, ChatMessageType type, Long senderId, String content) {
		ChatMessage message = new ChatMessage();
		message.chatRoomId = chatRoomId;
		message.type = type;
		message.senderId = senderId;
		message.content = content;
		return message;
	}

	public Long getId() {
		return id;
	}

	public Long getChatRoomId() {
		return chatRoomId;
	}

	public ChatMessageType getType() {
		return type;
	}

	public Long getSenderId() {
		return senderId;
	}

	public String getContent() {
		return content;
	}

}
