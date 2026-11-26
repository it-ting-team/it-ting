package com.itting.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.itting.chat.domain.ChatErrorCode;
import com.itting.chat.domain.ChatMessage;
import com.itting.chat.domain.ChatMessageImage;
import com.itting.chat.domain.ChatRoomMember;
import com.itting.chat.dto.ChatMessageResponse;
import com.itting.chat.dto.ChatMessageSendRequest;
import com.itting.chat.dto.ChatReadResponse;
import com.itting.chat.repository.ChatMessageImageRepository;
import com.itting.chat.repository.ChatMessageRepository;
import com.itting.chat.repository.ChatRoomMemberRepository;
import com.itting.chat.repository.ChatRoomReadRepository;
import com.itting.community.service.ImageUsageService;
import com.itting.global.error.BusinessException;
import com.itting.notification.service.NotificationService;
import com.itting.user.domain.User;
import com.itting.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 메시지 보내기와 읽음({@code POST /api/v1/chat-rooms/{chatRoomId}/messages}, {@code .../read}).
 *
 * <p>1:1 대화방에 보낼 때는 두 사람의 {@code user} 줄을 id가 작은 것부터 함께 읽기 잠금으로 잡은 뒤 읽기 전용인지 본다
 * ({@code docs/db/chat.md}). 상대의 대화 나가기는 같은 두 줄을 쓰기 잠금으로 잡으므로, 나가기와 보내기가 동시에 와도 "나갔습니다"
 * 뒤에 메시지가 들어가지 않는다. 함께 읽기 잠금이라 두 사람이 동시에 보내는 것은 서로 기다리지 않는다. 모임 대화방은 읽기 전용이
 * 없어 잠그지 않는다.
 */
@Service
public class ChatMessageService {

	private static final String UNUSABLE_IMAGE = "붙일 수 없는 사진이 있습니다.";

	private final ChatRoomAccess access;

	private final UserRepository userRepository;

	private final ChatRoomMemberRepository memberRepository;

	private final ChatMessageRepository messageRepository;

	private final ChatMessageImageRepository imageRepository;

	private final ChatRoomReadRepository readRepository;

	private final ChatMessageAssembler assembler;

	private final ImageUsageService imageUsageService;

	private final NotificationService notificationService;

	private final ChatEventPublisher events;

	private final TransactionTemplate transaction;

	public ChatMessageService(ChatRoomAccess access, UserRepository userRepository,
			ChatRoomMemberRepository memberRepository, ChatMessageRepository messageRepository,
			ChatMessageImageRepository imageRepository, ChatRoomReadRepository readRepository,
			ChatMessageAssembler assembler, ImageUsageService imageUsageService,
			NotificationService notificationService, ChatEventPublisher events, TransactionTemplate transaction) {
		this.access = access;
		this.userRepository = userRepository;
		this.memberRepository = memberRepository;
		this.messageRepository = messageRepository;
		this.imageRepository = imageRepository;
		this.readRepository = readRepository;
		this.assembler = assembler;
		this.imageUsageService = imageUsageService;
		this.notificationService = notificationService;
		this.events = events;
		this.transaction = transaction;
	}

	/**
	 * 대화방에 메시지를 보낸다. 글만, 사진만, 또는 둘 다 보낸다.
	 *
	 * <ol>
	 * <li>입력을 본다. 글도 사진도 없거나 쓸 수 없는 사진이면 {@code 400}.
	 * <li>들어갈 수 있는 대화방인지 본다. 아니면 {@code 404}.
	 * <li>1:1 대화방이면 두 사람을 함께 읽기 잠금으로 잡고, 상대가 나갔거나 탈퇴했으면 {@code 409}.
	 * <li>메시지와 사진을 저장하고, 내 읽은 자리를 이 메시지로 옮긴다.
	 * <li>대화방의 다른 사람에게 새 메시지 알림을 만들고, 커밋 뒤에 {@link ChatMessageCreatedEvent}를 낸다.
	 * </ol>
	 *
	 * @throws BusinessException {@code INVALID_INPUT}, {@code CHAT_ROOM_NOT_FOUND}, {@code CHAT_ROOM_READ_ONLY}
	 */
	public ChatMessageResponse send(Long userId, Long chatRoomId, ChatMessageSendRequest request) {
		List<Long> imageIds = request.images();
		String content = content(request.content(), imageIds);
		if (!imageUsageService.usable(userId, null, imageIds)) {
			throw BusinessException.invalidInput("imageIds", UNUSABLE_IMAGE);
		}
		boolean direct = access.require(userId, chatRoomId).room().isDirect();
		Long counterpartId = direct ? access.counterpart(userId, chatRoomId).getUserId() : null;
		return transaction.execute(status -> sendLocked(userId, chatRoomId, counterpartId, content, imageIds));
	}

	/**
	 * 이 대화방을 어디까지 읽었는지 기록한다. 읽은 자리는 뒤로만 옮기고, 그 대화방의 안 읽은 새 메시지 알림을 읽음으로 바꾼다.
	 * 상대에게는 아무것도 알리지 않는다.
	 *
	 * @return 기록한 뒤 이 대화방의 안 읽은 수
	 * @throws BusinessException 들어갈 수 없으면 {@code CHAT_ROOM_NOT_FOUND}, 이 대화방의 메시지가 아니면 {@code INVALID_INPUT}
	 */
	@Transactional
	public ChatReadResponse read(Long userId, Long chatRoomId, Long messageId) {
		access.require(userId, chatRoomId);
		if (!messageRepository.existsByIdAndChatRoomId(messageId, chatRoomId)) {
			throw BusinessException.invalidInput("messageId", "이 대화방의 메시지가 아닙니다.");
		}
		memberRepository.advanceLastRead(chatRoomId, userId, messageId);
		notificationService.readChatRoom(userId, chatRoomId);
		long unreadCount = readRepository.countUnreadByRoomIds(userId, List.of(chatRoomId))
				.getOrDefault(chatRoomId, 0L);
		return new ChatReadResponse(unreadCount);
	}

	private ChatMessageResponse sendLocked(Long userId, Long chatRoomId, Long counterpartId, String content,
			List<Long> imageIds) {
		Map<Long, User> users = counterpartId == null ? Map.of()
				: userRepository.findBothForShare(userId, counterpartId);
		access.require(userId, chatRoomId);
		if (counterpartId != null) {
			User counterpart = users.get(counterpartId);
			boolean counterpartJoined = memberRepository
					.findById(new ChatRoomMember.Key(chatRoomId, counterpartId))
					.filter(ChatRoomMember::isJoined)
					.isPresent();
			if (counterpart == null || !counterpart.isActive() || !counterpartJoined) {
				throw new BusinessException(ChatErrorCode.CHAT_ROOM_READ_ONLY);
			}
		}

		ChatMessage message = messageRepository.save(ChatMessage.text(chatRoomId, userId, content));
		attach(message.getId(), imageIds);
		// 받는 사람은 id 순서로 돈다. 같은 모임 대화방에 여러 사람이 동시에 보내도 알림 줄을 같은 순서로 잡아 교착하지 않는다.
		List<Long> receiverIds = memberRepository.findAllByChatRoomId(chatRoomId).stream()
				.filter(ChatRoomMember::isJoined)
				.map(ChatRoomMember::getUserId)
				.filter(memberId -> !memberId.equals(userId))
				.sorted()
				.toList();
		memberRepository.advanceLastRead(chatRoomId, userId, message.getId());
		receiverIds.forEach(receiverId -> notificationService.notifyChatMessage(receiverId, chatRoomId, userId));
		events.messageCreated(chatRoomId, message.getId());
		return assembler.assemble(message);
	}

	/**
	 * 사진을 {@code imageIds}의 순서대로 1부터 붙인다. 같은 사진을 다른 메시지가 그사이에 붙였으면({@code image_id} UNIQUE)
	 * 쓸 수 없는 사진으로 답한다.
	 */
	private void attach(Long messageId, List<Long> imageIds) {
		if (imageIds.isEmpty()) {
			return;
		}
		List<ChatMessageImage> images = new ArrayList<>();
		for (int i = 0; i < imageIds.size(); i++) {
			images.add(ChatMessageImage.create(messageId, i + 1, imageIds.get(i)));
		}
		try {
			imageRepository.saveAllAndFlush(images);
		}
		catch (DataIntegrityViolationException e) {
			throw BusinessException.invalidInput("imageIds", UNUSABLE_IMAGE);
		}
	}

	/**
	 * 저장할 본문. 공백뿐인 본문은 본문이 없는 것으로 본다. 사진이 있으면 사진만 보낸 메시지(null)이고, 사진도 없으면 보낼 것이
	 * 없어 {@code 400}이다. 그 밖의 본문은 앞뒤 공백을 다듬지 않고 받은 그대로 저장한다.
	 */
	private static String content(String content, List<Long> imageIds) {
		if (content != null && !content.isBlank()) {
			return content;
		}
		if (imageIds.isEmpty()) {
			throw BusinessException.invalidInput("content", "본문이나 사진이 있어야 합니다.");
		}
		return null;
	}

}
