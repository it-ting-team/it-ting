package com.itting.chat.service;

import java.time.Instant;
import java.util.Optional;

import com.itting.chat.domain.ChatMessage;
import com.itting.chat.domain.ChatRoom;
import com.itting.chat.domain.ChatRoomMember;
import com.itting.chat.repository.ChatMessageRepository;
import com.itting.chat.repository.ChatRoomMemberRepository;
import com.itting.chat.repository.ChatRoomRepository;
import com.itting.meetup.service.MeetupChatRooms;
import com.itting.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetupChatRoomService implements MeetupChatRooms {

	private final ChatRoomRepository rooms;
	private final ChatRoomMemberRepository members;
	private final ChatMessageRepository messages;
	private final NotificationService notifications;
	private final ChatEventPublisher events;

	public MeetupChatRoomService(ChatRoomRepository rooms, ChatRoomMemberRepository members,
			ChatMessageRepository messages, NotificationService notifications, ChatEventPublisher events) {
		this.rooms = rooms;
		this.members = members;
		this.messages = messages;
		this.notifications = notifications;
		this.events = events;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Long create(Long meetupId, Long hostId, Instant joinedAt) {
		Long roomId = rooms.save(ChatRoom.createMeetup(meetupId)).getId();
		members.save(ChatRoomMember.join(roomId, hostId, joinedAt, null));
		return roomId;
	}

	@Override
	public Optional<Long> findJoinedRoom(Long meetupId, Long userId) {
		return rooms.findJoinedMeetupRoomId(meetupId, userId);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void join(Long meetupId, Long userId, Instant now) {
		Long roomId = roomId(meetupId);
		members.save(ChatRoomMember.join(roomId, userId, now, null));
		ChatMessage message = messages.save(ChatMessage.joined(roomId, userId));
		events.messageCreated(roomId, message.getId());
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void leave(Long meetupId, Long userId, Instant now) {
		Long roomId = roomId(meetupId);
		ChatRoomMember member = members.findById(new ChatRoomMember.Key(roomId, userId))
				.orElseThrow(() -> new IllegalStateException("확정 참여자의 모임 대화방 멤버가 없습니다."));
		member.leave(now);
		ChatMessage message = messages.save(ChatMessage.left(roomId, userId));
		// 빠진 사람은 이 대화방에 들어갈 수 없으므로, 그 대화방의 안 읽은 새 메시지 알림을 읽음으로 바꾼다.
		notifications.readChatRoom(userId, roomId);
		events.messageCreated(roomId, message.getId());
	}

	private Long roomId(Long meetupId) {
		return rooms.findByMeetupId(meetupId).map(ChatRoom::getId)
				.orElseThrow(() -> new IllegalStateException("모임 대화방이 없습니다."));
	}

}
