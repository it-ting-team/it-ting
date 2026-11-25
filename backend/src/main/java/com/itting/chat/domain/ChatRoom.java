package com.itting.chat.domain;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 1:1 대화방과 모임 대화방({@code chat_room}). 1:1 방은 대화 요청을 수락하면 만들고, 모임 대화방은 모임을 만드는 작업이 붙인다.
 */
@Entity
@Table(name = "chat_room")
public class ChatRoom extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private ChatRoomType type;

	/** 모임 대화방의 모임. 1:1 방은 비어 있다. */
	@Column(name = "meetup_id", updatable = false)
	private Long meetupId;

	/** 모임 대화방의 공지. */
	@Column(columnDefinition = "TEXT")
	private String notice;

	protected ChatRoom() {
	}

	/** 새 1:1 대화방. */
	public static ChatRoom createDirect() {
		ChatRoom room = new ChatRoom();
		room.type = ChatRoomType.DIRECT;
		return room;
	}

	public static ChatRoom createMeetup(Long meetupId) {
		ChatRoom room = new ChatRoom();
		room.type = ChatRoomType.MEETUP;
		room.meetupId = meetupId;
		return room;
	}

	public Long getId() {
		return id;
	}

	public ChatRoomType getType() {
		return type;
	}

	public Long getMeetupId() {
		return meetupId;
	}

	public String getNotice() {
		return notice;
	}

}
