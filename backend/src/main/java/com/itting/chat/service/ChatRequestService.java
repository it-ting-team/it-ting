package com.itting.chat.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.itting.chat.domain.ChatErrorCode;
import com.itting.chat.domain.ChatRequest;
import com.itting.chat.domain.ChatRequestStatus;
import com.itting.chat.dto.ChatRequestAcceptResponse;
import com.itting.chat.dto.ChatRequestAcceptedDetailResponse;
import com.itting.chat.dto.ChatRequestCreateRequest;
import com.itting.chat.dto.ChatRequestCreateResponse;
import com.itting.chat.repository.ChatRequestParties;
import com.itting.chat.repository.ChatRequestRepository;
import com.itting.chat.repository.ChatRoomRepository;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.notification.domain.NotificationType;
import com.itting.notification.service.NotificationService;
import com.itting.user.domain.User;
import com.itting.user.domain.UserErrorCode;
import com.itting.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대화 요청을 보내고, 수락 · 거절 · 취소한다({@code docs/api/05-explore-visit.md}, {@code docs/api/06-chat.md}).
 *
 * <p>네 가지 일 모두 같은 순서로 처리한다({@code docs/db/chat.md}).
 * <ol>
 * <li>잠그지 않고 입력과 대상을 본다. 없는 대상은 잠그지 않는다(없는 계정 id를 잠그면 id 사이의 빈 곳이 잠겨 다른 계정의 가입이
 * 기다린다).
 * <li>트랜잭션을 열고, 두 사람의 {@code user} 줄을 id가 작은 것부터 잠근다. 이것이 트랜잭션의 첫 SQL이다. 같은 두 사람의 일은 한
 * 번에 하나씩만 처리된다.
 * <li>잠근 뒤에 요청과 대화방을 읽어 판단한다. 잠그기 전에 일반 조회를 하면 기다리는 사이에 다른 트랜잭션이 커밋한 것이 보이지
 * 않으므로, 잠그기 전의 조회는 트랜잭션 밖에서 한다.
 * </ol>
 * 서로 동시에 보내도 요청은 하나이고, 같은 요청을 동시에 수락해도 대화방은 하나다.
 */
@Service
public class ChatRequestService {

	/** 살아 있는 요청의 상태. 받은 사람에게는 {@code PENDING}만 보이지만, 두 사람 사이에는 둘 다 새 요청을 막는다. */
	private static final List<ChatRequestStatus> ALIVE = List.of(ChatRequestStatus.PENDING,
			ChatRequestStatus.REJECTED);

	private final UserRepository userRepository;

	private final ChatRequestRepository chatRequestRepository;

	private final ChatRoomRepository chatRoomRepository;

	private final DirectChatOpener directChatOpener;

	private final NotificationService notificationService;

	private final ChatEventPublisher events;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public ChatRequestService(UserRepository userRepository, ChatRequestRepository chatRequestRepository,
			ChatRoomRepository chatRoomRepository, DirectChatOpener directChatOpener,
			NotificationService notificationService, ChatEventPublisher events, TransactionTemplate transaction,
			Clock clock) {
		this.userRepository = userRepository;
		this.chatRequestRepository = chatRequestRepository;
		this.chatRoomRepository = chatRoomRepository;
		this.directChatOpener = directChatOpener;
		this.notificationService = notificationService;
		this.events = events;
		this.transaction = transaction;
		this.clock = clock;
	}

	/**
	 * 다른 사람에게 1:1 대화 요청을 보낸다. 내가 거절한 요청이 남아 있는 사람에게 보내면 요청을 만들지 않고 그 요청을 내가 수락한
	 * 것으로 처리해 대화방을 바로 연다. 이때 인사는 내가 보낸 첫 메시지가 된다.
	 *
	 * @throws BusinessException 나 자신이면 {@code INVALID_INPUT}, 가입을 마치지 않았거나 없는 사람이면 {@code USER_NOT_FOUND},
	 *         이미 대화 중이면 {@code CHAT_ROOM_ALREADY_EXISTS}, 내가 보낸 살아 있는 요청이 있으면
	 *         {@code CHAT_REQUEST_ALREADY_SENT}, 상대가 보낸 요청에 아직 답하지 않았으면 {@code CHAT_REQUEST_ALREADY_RECEIVED}
	 */
	public ChatRequestCreateResponse send(Long senderId, ChatRequestCreateRequest request) {
		Long receiverId = request.receiverId();
		if (receiverId.equals(senderId)) {
			throw BusinessException.invalidInput("receiverId", "나 자신에게는 보낼 수 없습니다.");
		}
		String greeting = request.message() == null || request.message().isEmpty() ? null : request.message();
		userRepository.findById(receiverId)
				.filter(ChatRequestService::canReceive)
				.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
		return transaction.execute(status -> sendLocked(senderId, receiverId, greeting));
	}

	private ChatRequestCreateResponse sendLocked(Long senderId, Long receiverId, String greeting) {
		Map<Long, User> users = userRepository.findBothForUpdate(senderId, receiverId);
		User sender = users.get(senderId);
		if (sender == null || !sender.isActive()) {
			throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
		}
		User receiver = users.get(receiverId);
		if (receiver == null || !canReceive(receiver)) {
			throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
		}

		Instant aliveAfter = ChatRequest.aliveAfter(now());
		if (!chatRoomRepository.findOpenDirectRoomIds(senderId, receiverId).isEmpty()) {
			throw new BusinessException(ChatErrorCode.CHAT_ROOM_ALREADY_EXISTS);
		}
		if (!chatRequestRepository.findAlive(senderId, receiverId, ALIVE, aliveAfter).isEmpty()) {
			throw new BusinessException(ChatErrorCode.CHAT_REQUEST_ALREADY_SENT);
		}
		List<ChatRequest> incoming = chatRequestRepository.findAlive(receiverId, senderId, ALIVE, aliveAfter);
		if (!incoming.isEmpty()) {
			ChatRequest theirs = incoming.getFirst();
			if (theirs.getStatus() == ChatRequestStatus.PENDING) {
				throw new BusinessException(ChatErrorCode.CHAT_REQUEST_ALREADY_RECEIVED);
			}
			// 내가 거절한 요청이다. 새 요청을 만들지 않고 내가 수락한 것으로 처리한다. 상대에게는 거절됐던 것이 드러나지 않는다.
			return ChatRequestCreateResponse.opened(directChatOpener.open(theirs, greeting));
		}

		ChatRequest created = chatRequestRepository.save(ChatRequest.create(senderId, receiverId, greeting));
		notificationService.notify(receiverId, NotificationType.CHAT_REQUEST_RECEIVED, senderId, created.getId());
		events.requestCountChanged(receiverId);
		return ChatRequestCreateResponse.sent(created);
	}

	/**
	 * 받은 요청을 수락해 1:1 대화방을 만든다. 이미 수락한 요청이면 아무것도 바꾸지 않고 그때 만든 대화방의 id를 준다.
	 *
	 * @throws BusinessException 없거나, 내가 받은 요청이 아니거나, 취소됐거나, 만료됐거나, 내가 이미 거절한 요청이면
	 *         {@code CHAT_REQUEST_NOT_FOUND}
	 */
	public ChatRequestAcceptResponse accept(Long userId, Long chatRequestId) {
		ChatRequestParties parties = findParties(chatRequestId, userId, true);
		return transaction.execute(status -> {
			userRepository.findBothForUpdate(parties.senderId(), parties.receiverId());
			ChatRequest request = chatRequestRepository.findById(chatRequestId)
					.orElseThrow(ChatRequestService::notFound);
			if (request.getStatus() == ChatRequestStatus.ACCEPTED) {
				return new ChatRequestAcceptResponse(request.getChatRoomId());
			}
			if (request.getStatus() != ChatRequestStatus.PENDING || !request.isAlive(now())) {
				throw notFound();
			}
			Long chatRoomId = directChatOpener.open(request, null);
			events.requestCountChanged(userId);
			return new ChatRequestAcceptResponse(chatRoomId);
		});
	}

	/**
	 * 받은 요청을 거절한다. 보낸 사람에게는 알리지 않는다. 이미 거절한 요청을 다시 거절해도 성공이다.
	 *
	 * @throws BusinessException 없거나, 내가 받은 요청이 아니거나, 취소됐거나, 만료됐거나, 이미 수락한 요청이면
	 *         {@code CHAT_REQUEST_NOT_FOUND}
	 */
	public void reject(Long userId, Long chatRequestId) {
		ChatRequestParties parties = findParties(chatRequestId, userId, true);
		transaction.executeWithoutResult(status -> {
			userRepository.findBothForUpdate(parties.senderId(), parties.receiverId());
			ChatRequest request = chatRequestRepository.findById(chatRequestId)
					.orElseThrow(ChatRequestService::notFound);
			Instant now = now();
			if (request.getStatus() == ChatRequestStatus.REJECTED && request.isAlive(now)) {
				return;
			}
			if (request.getStatus() != ChatRequestStatus.PENDING || !request.isAlive(now)) {
				throw notFound();
			}
			request.reject(now);
			events.requestCountChanged(userId);
		});
	}

	/**
	 * 내가 보낸 요청을 취소한다. 받은 사람이 거절한 요청도 {@code PENDING}일 때와 똑같이 취소되고 응답도 같다. 거절을 드러내지
	 * 않기 위해서다. 알림은 만들지 않는다.
	 *
	 * @throws BusinessException 없거나, 내가 보낸 요청이 아니거나, 이미 취소했거나, 만료됐으면
	 *         {@code CHAT_REQUEST_NOT_FOUND}. 상대가 이미 수락했으면 {@code CHAT_REQUEST_ALREADY_ACCEPTED}와
	 *         {@code detail.chatRoomId}
	 */
	public void cancel(Long userId, Long chatRequestId) {
		ChatRequestParties parties = findParties(chatRequestId, userId, false);
		transaction.executeWithoutResult(status -> {
			userRepository.findBothForUpdate(parties.senderId(), parties.receiverId());
			ChatRequest request = chatRequestRepository.findById(chatRequestId)
					.orElseThrow(ChatRequestService::notFound);
			if (request.getStatus() == ChatRequestStatus.ACCEPTED) {
				throw new BusinessException(ChatErrorCode.CHAT_REQUEST_ALREADY_ACCEPTED,
						new ChatRequestAcceptedDetailResponse(request.getChatRoomId()));
			}
			Instant now = now();
			if (!request.isAlive(now)) {
				throw notFound();
			}
			boolean wasPending = request.getStatus() == ChatRequestStatus.PENDING;
			request.cancel(now);
			if (wasPending) {
				// 받은 사람의 받은 요청에서 카드가 사라진다. 거절된 요청은 이미 받은 요청이 아니라 수가 바뀌지 않는다.
				events.requestCountChanged(request.getReceiverId());
			}
		});
	}

	/**
	 * 요청의 두 사람을 잠그지 않고 읽고, 내가 그 요청의 받은 사람(또는 보낸 사람)인지 본다. 아니면 없는 요청과 같게 답한다.
	 */
	private ChatRequestParties findParties(Long chatRequestId, Long userId, boolean asReceiver) {
		return chatRequestRepository.findParties(chatRequestId)
				.filter(parties -> userId.equals(asReceiver ? parties.receiverId() : parties.senderId()))
				.orElseThrow(ChatRequestService::notFound);
	}

	/** 요청을 받을 수 있는 사람: 활동 중이고 온보딩까지 마친 계정. */
	private static boolean canReceive(User user) {
		return user.isActive() && user.isOnboarded();
	}

	private static BusinessException notFound() {
		return new BusinessException(ChatErrorCode.CHAT_REQUEST_NOT_FOUND);
	}

	private Instant now() {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

}
