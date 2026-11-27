package com.itting.chat.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.itting.chat.domain.ChatErrorCode;
import com.itting.chat.domain.ChatMessage;
import com.itting.chat.domain.ChatRoom;
import com.itting.chat.domain.ChatRoomMember;
import com.itting.chat.dto.ChatNoticeResponse;
import com.itting.chat.repository.ChatMessageRepository;
import com.itting.chat.repository.ChatRoomMemberRepository;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.meetup.service.MeetupChatInfoReader;
import com.itting.meetup.service.MeetupChatInfoReader.MeetupChatInfo;
import com.itting.notification.service.NotificationService;
import com.itting.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대화방에서 나가기와 모임 대화방의 공지({@code POST /api/v1/chat-rooms/{chatRoomId}/leave},
 * {@code PUT · DELETE .../notice}).
 *
 * <p>나가기는 대화방에서만 나간다. 모임의 참여는 바꾸지 않는다. 끝나지 않은 모임에서 나가는 일은 모임의 참여 취소가 하고, 그때
 * 모임 API가 대화방에서도 뺀다({@link MeetupChatRoomService}).
 */
@Service
public class ChatRoomService {

	private final ChatRoomAccess access;

	private final UserRepository userRepository;

	private final ChatRoomMemberRepository memberRepository;

	private final ChatMessageRepository messageRepository;

	private final MeetupChatInfoReader meetups;

	private final NotificationService notificationService;

	private final ChatEventPublisher events;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public ChatRoomService(ChatRoomAccess access, UserRepository userRepository, ChatRoomMemberRepository memberRepository,
			ChatMessageRepository messageRepository, MeetupChatInfoReader meetups,
			NotificationService notificationService, ChatEventPublisher events, TransactionTemplate transaction,
			Clock clock) {
		this.access = access;
		this.userRepository = userRepository;
		this.memberRepository = memberRepository;
		this.messageRepository = messageRepository;
		this.meetups = meetups;
		this.notificationService = notificationService;
		this.events = events;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * 대화방에서 나간다. 내 {@code left_at}을 채우고 {@code LEFT} 안내 메시지를 넣는다. 한 번 나간 대화방에는 다시 들어갈 수 없다.
	 *
	 * <ul>
	 * <li>1:1 대화방: 두 사람의 {@code user} 줄을 id가 작은 것부터 잠근 뒤 한다. 상대의 대화방은 읽기 전용이 되고, 그 뒤로는
	 * 두 사람 모두 상대에게 대화 요청을 보낼 수 있다.
	 * <li>모임 대화방: 모임이 취소됐거나 끝났을 때만 나갈 수 있다. 주최자도 나갈 수 있고 참여 기록은 그대로다.
	 * </ul>
	 * 나간 사람의 그 대화방 새 메시지 알림은 읽음으로 바꾼다(들어갈 수 없는 대화방의 알림이 안 읽은 채 남지 않게).
	 *
	 * @throws BusinessException 들어갈 수 없으면 {@code CHAT_ROOM_NOT_FOUND}, 끝나지 않은 모임의 대화방이면
	 *         {@code CHAT_ROOM_LEAVE_NOT_ALLOWED}
	 */
	public void leave(Long userId, Long chatRoomId) {
		ChatRoom room = access.require(userId, chatRoomId).room();
		if (room.isDirect()) {
			Long counterpartId = access.counterpart(userId, chatRoomId).getUserId();
			transaction.executeWithoutResult(status -> {
				userRepository.findBothForUpdate(userId, counterpartId);
				leaveLocked(userId, chatRoomId);
			});
			return;
		}
		Long meetupId = room.getMeetupId();
		transaction.executeWithoutResult(status -> {
			ChatRoomMember member = lockMember(userId, chatRoomId);
			MeetupChatInfo meetup = meetups.find(meetupId).orElseThrow(ChatRoomAccess::notFound);
			if (!meetup.isOver()) {
				throw new BusinessException(ChatErrorCode.CHAT_ROOM_LEAVE_NOT_ALLOWED);
			}
			leave(member);
		});
	}

	/**
	 * 모임 대화방의 공지를 쓰거나 고친다. 공지는 대화방에 하나뿐이라 있으면 바꾸고 없으면 새로 건다. 다른 참여자에게 실시간으로
	 * 알리지 않는다.
	 *
	 * @throws BusinessException 들어갈 수 없으면 {@code CHAT_ROOM_NOT_FOUND}, 주최자가 아니거나 1:1 대화방이면 {@code FORBIDDEN}
	 */
	@Transactional
	public ChatNoticeResponse writeNotice(Long userId, Long chatRoomId, String content) {
		requireHostedRoom(userId, chatRoomId).changeNotice(content);
		return new ChatNoticeResponse(content);
	}

	/**
	 * 모임 대화방의 공지를 지운다. 공지가 없었어도 성공이다.
	 *
	 * @throws BusinessException 들어갈 수 없으면 {@code CHAT_ROOM_NOT_FOUND}, 주최자가 아니거나 1:1 대화방이면 {@code FORBIDDEN}
	 */
	@Transactional
	public void deleteNotice(Long userId, Long chatRoomId) {
		requireHostedRoom(userId, chatRoomId).changeNotice(null);
	}

	private void leaveLocked(Long userId, Long chatRoomId) {
		leave(lockMember(userId, chatRoomId));
	}

	/** 내 참여자 줄을 잠그고 읽는다. 그사이에 나갔으면(같은 요청이 겹쳤으면) 들어갈 수 없는 대화방이다. */
	private ChatRoomMember lockMember(Long userId, Long chatRoomId) {
		return memberRepository.findForUpdate(chatRoomId, userId)
				.filter(ChatRoomMember::isJoined)
				.orElseThrow(ChatRoomAccess::notFound);
	}

	private void leave(ChatRoomMember member) {
		Long chatRoomId = member.getChatRoomId();
		Long userId = member.getUserId();
		Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
		member.leave(now);
		memberRepository.saveAndFlush(member);
		ChatMessage message = messageRepository.save(ChatMessage.left(chatRoomId, userId));
		notificationService.readChatRoom(userId, chatRoomId);
		events.messageCreated(chatRoomId, message.getId());
	}

	/** 내가 주최자인 모임 대화방. */
	private ChatRoom requireHostedRoom(Long userId, Long chatRoomId) {
		ChatRoom room = access.require(userId, chatRoomId).room();
		if (room.isDirect()) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN);
		}
		MeetupChatInfo meetup = meetups.find(room.getMeetupId()).orElseThrow(ChatRoomAccess::notFound);
		if (!meetup.hostId().equals(userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN);
		}
		return room;
	}

}
