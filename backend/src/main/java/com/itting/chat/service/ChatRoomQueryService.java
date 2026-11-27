package com.itting.chat.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.itting.chat.domain.ChatLeaveType;
import com.itting.chat.domain.ChatMessage;
import com.itting.chat.domain.ChatRoom;
import com.itting.chat.domain.ChatRoomMember;
import com.itting.chat.dto.ChatMessageListResponse;
import com.itting.chat.dto.ChatMessageResponse;
import com.itting.chat.dto.ChatRoomDetailResponse;
import com.itting.chat.dto.ChatRoomDetailResponse.MeetupInfo;
import com.itting.chat.dto.ChatRoomDetailResponse.Member;
import com.itting.chat.dto.ChatRoomListResponse;
import com.itting.chat.dto.ChatRoomListResponse.ChatRoomSummary;
import com.itting.chat.dto.ChatRoomListResponse.MeetupRef;
import com.itting.chat.repository.ChatMessageRepository;
import com.itting.chat.repository.ChatRoomMemberRepository;
import com.itting.chat.repository.ChatRoomReadRepository;
import com.itting.chat.repository.ChatRoomReadRepository.JoinedRoom;
import com.itting.interest.dto.InterestTagResponse;
import com.itting.interest.service.InterestTagService;
import com.itting.meetup.service.MeetupChatInfoReader;
import com.itting.meetup.service.MeetupChatInfoReader.MeetupChatInfo;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.UserDisplayService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대화 목록, 대화방 조회, 메시지 조회({@code GET /api/v1/me/chat-rooms}, {@code GET /api/v1/chat-rooms/{chatRoomId}},
 * {@code .../messages}). 들어갈 수 있는 대화방만 준다. 조회만으로는 읽은 자리가 바뀌지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class ChatRoomQueryService {

	private final ChatRoomAccess access;

	private final ChatRoomReadRepository readRepository;

	private final ChatRoomMemberRepository memberRepository;

	private final ChatMessageRepository messageRepository;

	private final ChatMessageAssembler assembler;

	private final UserDisplayService userDisplayService;

	private final InterestTagService interestTagService;

	private final MeetupChatInfoReader meetups;

	public ChatRoomQueryService(ChatRoomAccess access, ChatRoomReadRepository readRepository,
			ChatRoomMemberRepository memberRepository, ChatMessageRepository messageRepository,
			ChatMessageAssembler assembler, UserDisplayService userDisplayService,
			InterestTagService interestTagService, MeetupChatInfoReader meetups) {
		this.access = access;
		this.readRepository = readRepository;
		this.memberRepository = memberRepository;
		this.messageRepository = messageRepository;
		this.assembler = assembler;
		this.userDisplayService = userDisplayService;
		this.interestTagService = interestTagService;
		this.meetups = meetups;
	}

	/**
	 * 내가 들어가 있는 대화방을 한 번에 모두 준다. 마지막 메시지가 새로운 것부터이고, 메시지가 없는 대화방은 만든 시각으로 줄을
	 * 세운다. 내가 나간 대화방과 삭제된 모임의 대화방은 없다.
	 */
	public ChatRoomListResponse list(Long userId) {
		List<JoinedRoom> rooms = readRepository.findJoinedRooms(userId);
		Map<Long, ChatMessageResponse> lastMessages = assembler
				.assemble(messageRepository.findAllById(rooms.stream()
						.map(JoinedRoom::lastMessageId)
						.filter(Objects::nonNull)
						.toList()))
				.stream()
				.collect(Collectors.toMap(ChatMessageResponse::id, Function.identity()));
		Map<Long, UserDisplayResponse> counterparts = userDisplayService.getAll(rooms.stream()
				.map(JoinedRoom::counterpartId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet()));

		List<ChatRoomSummary> items = rooms.stream()
				.sorted(Comparator
						.comparing((JoinedRoom room) -> latestAt(room, lastMessages), Comparator.reverseOrder())
						.thenComparing(JoinedRoom::id, Comparator.reverseOrder()))
				.map(room -> {
					UserDisplayResponse counterpart = room.counterpartId() == null ? null
							: counterparts.get(room.counterpartId());
					boolean readOnly = counterpart != null && (room.counterpartLeft() || counterpart.isWithdrawn());
					return new ChatRoomSummary(room.id(), room.type(), counterpart,
							room.meetupId() == null ? null : new MeetupRef(room.meetupId(), room.meetupTitle()),
							room.lastMessageId() == null ? null : lastMessages.get(room.lastMessageId()),
							room.unreadCount(), readOnly);
				})
				.toList();
		return new ChatRoomListResponse(items);
	}

	/**
	 * 대화방 하나를 열 때 필요한 값. 1:1은 상대와 겹치는 관심사를, 모임은 모임 · 공지 · 지금 있는 사람을 채운다.
	 *
	 * @throws com.itting.global.error.BusinessException 들어갈 수 없으면 {@code CHAT_ROOM_NOT_FOUND}
	 */
	public ChatRoomDetailResponse detail(Long userId, Long chatRoomId) {
		ChatRoom room = access.require(userId, chatRoomId).room();
		List<ChatRoomMember> roomMembers = memberRepository.findAllByChatRoomId(chatRoomId);
		return room.isDirect() ? directDetail(userId, room, roomMembers) : meetupDetail(userId, room, roomMembers);
	}

	/**
	 * 대화방의 메시지를 새 것부터 나눠 준다. 내가 들어오기 전의 메시지와 안내 메시지도 준다.
	 *
	 * @param cursor 앞 응답의 {@code nextCursor}. 처음에는 null
	 * @throws com.itting.global.error.BusinessException 들어갈 수 없으면 {@code CHAT_ROOM_NOT_FOUND}, 읽을 수 없는 커서면
	 *         {@code INVALID_INPUT}
	 */
	public ChatMessageListResponse messages(Long userId, Long chatRoomId, int size, String cursor) {
		Long beforeId = cursor == null ? null : ChatMessageCursor.decode(cursor).id();
		access.require(userId, chatRoomId);
		Limit limit = Limit.of(size + 1);
		List<ChatMessage> page = beforeId == null ? messageRepository.findByChatRoomIdOrderByIdDesc(chatRoomId, limit)
				: messageRepository.findByChatRoomIdAndIdLessThanOrderByIdDesc(chatRoomId, beforeId, limit);
		boolean hasNext = page.size() > size;
		List<ChatMessage> items = hasNext ? page.subList(0, size) : page;
		String nextCursor = hasNext ? new ChatMessageCursor(items.getLast().getId()).encode() : null;
		return new ChatMessageListResponse(assembler.assemble(items), nextCursor, hasNext);
	}

	private ChatRoomDetailResponse directDetail(Long userId, ChatRoom room, List<ChatRoomMember> roomMembers) {
		ChatRoomMember other = ChatRoomAccess.counterpart(userId, roomMembers);
		UserDisplayResponse counterpart = userDisplayService.get(other.getUserId());
		boolean readOnly = !other.isJoined() || counterpart.isWithdrawn();
		List<InterestTagResponse> sharedInterests = counterpart.isWithdrawn() ? List.of()
				: interestTagService.getSharedTags(List.of(other.getUserId()), userId)
						.getOrDefault(other.getUserId(), List.of());
		return new ChatRoomDetailResponse(room.getId(), room.getType(), readOnly, ChatLeaveType.LEAVE_ROOM,
				counterpart, sharedInterests, null, null, false, List.of());
	}

	private ChatRoomDetailResponse meetupDetail(Long userId, ChatRoom room, List<ChatRoomMember> roomMembers) {
		MeetupChatInfo meetup = meetups.find(room.getMeetupId()).orElseThrow(ChatRoomAccess::notFound);
		boolean host = meetup.hostId().equals(userId);
		List<ChatRoomMember> joined = roomMembers.stream()
				.filter(ChatRoomMember::isJoined)
				.sorted(Comparator.comparing((ChatRoomMember member) -> !member.getUserId().equals(meetup.hostId()))
						.thenComparing(ChatRoomMember::getJoinedAt)
						.thenComparing(ChatRoomMember::getUserId))
				.toList();
		Map<Long, UserDisplayResponse> displays = userDisplayService.getAll(joined.stream()
				.map(ChatRoomMember::getUserId)
				.toList());
		List<Member> members = joined.stream()
				.map(member -> new Member(displays.get(member.getUserId()),
						member.getUserId().equals(meetup.hostId()), member.getUserId().equals(userId)))
				.toList();
		MeetupInfo info = new MeetupInfo(meetup.id(), meetup.hostId(), meetup.title(), meetup.category(),
				meetup.startsAt(), meetup.endsAt(), meetup.mode(), meetup.place(), meetup.placeDetail(),
				meetup.joinUrl(), meetup.capacity(), meetup.confirmedCount(), meetup.isCanceled());
		return new ChatRoomDetailResponse(room.getId(), room.getType(), false, leaveType(meetup, host), null,
				List.of(), info, room.getNotice(), host, members);
	}

	/**
	 * 모임 대화방의 나가기 버튼이 지금 하는 일. 취소됐거나 끝난 모임은 대화방에서만 나간다. 아니면 주최자는 나갈 수 없고, 참여자는
	 * 시작 24시간 전까지 모임의 참여 취소로 나간다.
	 */
	static ChatLeaveType leaveType(MeetupChatInfo meetup, boolean host) {
		if (meetup.isOver()) {
			return ChatLeaveType.LEAVE_ROOM;
		}
		if (host) {
			return ChatLeaveType.NOT_ALLOWED;
		}
		return meetup.canCancelParticipation() ? ChatLeaveType.CANCEL_PARTICIPATION : ChatLeaveType.NOT_ALLOWED;
	}

	/** 대화 목록의 순서를 정하는 시각. 마지막 메시지의 시각이고, 메시지가 없으면 방을 만든 시각이다. */
	private static Instant latestAt(JoinedRoom room, Map<Long, ChatMessageResponse> lastMessages) {
		ChatMessageResponse last = room.lastMessageId() == null ? null : lastMessages.get(room.lastMessageId());
		return last == null ? room.createdAt() : last.createdAt();
	}

}
