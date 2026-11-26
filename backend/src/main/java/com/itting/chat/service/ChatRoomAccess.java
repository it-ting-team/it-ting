package com.itting.chat.service;

import java.util.List;

import com.itting.chat.domain.ChatErrorCode;
import com.itting.chat.domain.ChatRoom;
import com.itting.chat.domain.ChatRoomMember;
import com.itting.chat.repository.ChatRoomMemberRepository;
import com.itting.chat.repository.ChatRoomRepository;
import com.itting.global.error.BusinessException;
import com.itting.meetup.service.MeetupChatInfoReader;
import org.springframework.stereotype.Component;

/**
 * 대화방에 들어갈 수 있는지를 한 곳에서 본다({@code docs/api/06-chat.md}의 "대화방에 들어갈 수 있는 사람"). 대화방 조회, 메시지
 * 조회와 보내기, 읽음, 나가기, 공지가 모두 이 검사를 쓴다. 대화 목록은 같은 조건을 SQL로 건다
 * ({@code ChatRoomReadRepository}).
 *
 * <ul>
 * <li>{@code chat_room_member}에 내 줄이 있고 {@code left_at}이 비어 있어야 한다.
 * <li>모임 대화방은 모임이 삭제되지 않았어야 한다.
 * </ul>
 * 아니면 {@code 404 CHAT_ROOM_NOT_FOUND}다. 없는 대화방과 들어갈 수 없는 대화방을 구분하지 않는다.
 */
@Component
class ChatRoomAccess {

	private final ChatRoomRepository rooms;

	private final ChatRoomMemberRepository members;

	private final MeetupChatInfoReader meetups;

	ChatRoomAccess(ChatRoomRepository rooms, ChatRoomMemberRepository members, MeetupChatInfoReader meetups) {
		this.rooms = rooms;
		this.members = members;
		this.meetups = meetups;
	}

	/**
	 * @return 대화방과 내 참여자 줄
	 * @throws BusinessException 들어갈 수 없으면 {@code CHAT_ROOM_NOT_FOUND}
	 */
	Entry require(Long userId, Long chatRoomId) {
		ChatRoomMember member = members.findById(new ChatRoomMember.Key(chatRoomId, userId))
				.filter(ChatRoomMember::isJoined)
				.orElseThrow(ChatRoomAccess::notFound);
		ChatRoom room = rooms.findById(chatRoomId).orElseThrow(ChatRoomAccess::notFound);
		if (!room.isDirect() && !meetups.exists(room.getMeetupId())) {
			throw notFound();
		}
		return new Entry(room, member);
	}

	/**
	 * 1:1 대화방의 상대. 나갔어도 준다.
	 *
	 * @throws IllegalStateException 1:1 대화방에 상대의 줄이 없으면(대화방은 늘 두 사람의 줄과 함께 만든다)
	 */
	ChatRoomMember counterpart(Long userId, Long chatRoomId) {
		return counterpart(userId, members.findAllByChatRoomId(chatRoomId));
	}

	static ChatRoomMember counterpart(Long userId, List<ChatRoomMember> roomMembers) {
		return roomMembers.stream()
				.filter(member -> !member.getUserId().equals(userId))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("1:1 대화방에 상대의 참여자 줄이 없습니다."));
	}

	static BusinessException notFound() {
		return new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
	}

	/** 들어갈 수 있는 대화방과 내 참여자 줄. */
	record Entry(ChatRoom room, ChatRoomMember member) {
	}

}
