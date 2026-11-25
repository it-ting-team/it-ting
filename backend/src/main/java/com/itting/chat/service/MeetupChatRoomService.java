package com.itting.chat.service;

import java.time.Instant;
import java.util.Optional;

import com.itting.chat.domain.ChatRoom;
import com.itting.chat.domain.ChatRoomMember;
import com.itting.chat.repository.ChatRoomMemberRepository;
import com.itting.chat.repository.ChatRoomRepository;
import com.itting.meetup.service.MeetupChatRooms;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetupChatRoomService implements MeetupChatRooms {

	private final ChatRoomRepository rooms;
	private final ChatRoomMemberRepository members;

	public MeetupChatRoomService(ChatRoomRepository rooms, ChatRoomMemberRepository members) {
		this.rooms = rooms;
		this.members = members;
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

}
