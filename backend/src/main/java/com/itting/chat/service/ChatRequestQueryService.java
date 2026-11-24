package com.itting.chat.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.itting.chat.domain.ChatRequest;
import com.itting.chat.domain.ChatRequestDirection;
import com.itting.chat.domain.ChatRequestStatus;
import com.itting.chat.dto.ChatRequestCountResponse;
import com.itting.chat.dto.ChatRequestListResponse;
import com.itting.chat.dto.ChatRequestListResponse.ChatRequestResponse;
import com.itting.chat.repository.ChatRequestRepository;
import com.itting.interest.dto.InterestTagResponse;
import com.itting.interest.service.InterestTagService;
import com.itting.notification.service.ChatRequestReader;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.UserDisplayService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내가 받거나 보낸 대화 요청의 목록과 수({@code GET /api/v1/me/chat-requests}, {@code .../count}). 알림 팝업의 "채팅 요청"
 * 줄도 같은 기준으로 센다({@link ChatRequestReader}).
 *
 * <p>살아 있는 요청만 센다. 받은 사람에게는 보낸 지 5일이 지나지 않은 {@code PENDING}만 보이고, 보낸 사람에게는
 * {@code PENDING}과 {@code REJECTED}가 똑같이 보인다(거절은 보낸 사람에게 드러나지 않는다). 목록의 개수와 수는 늘 같다.
 */
@Service
@Transactional(readOnly = true)
public class ChatRequestQueryService implements ChatRequestReader {

	/** 받은 사람에게 보이는 요청의 상태. 거절한 요청은 받은 요청에서 빠진다. */
	private static final List<ChatRequestStatus> RECEIVED = List.of(ChatRequestStatus.PENDING);

	/** 보낸 사람에게 보이는 요청의 상태. {@code REJECTED}도 {@code PENDING}처럼 보인다. */
	private static final List<ChatRequestStatus> SENT = List.of(ChatRequestStatus.PENDING, ChatRequestStatus.REJECTED);

	private final ChatRequestRepository chatRequestRepository;

	private final UserDisplayService userDisplayService;

	private final InterestTagService interestTagService;

	private final Clock clock;

	public ChatRequestQueryService(ChatRequestRepository chatRequestRepository,
			UserDisplayService userDisplayService, InterestTagService interestTagService, Clock clock) {
		this.chatRequestRepository = chatRequestRepository;
		this.userDisplayService = userDisplayService;
		this.interestTagService = interestTagService;
		this.clock = clock;
	}

	/**
	 * 받은 요청이나 보낸 요청의 목록. 새로 보낸 것부터 한 번에 모두 준다. 카드마다 상대와 겹치는 관심사 태그를 싣는다.
	 */
	public ChatRequestListResponse list(Long userId, ChatRequestDirection direction) {
		Instant aliveAfter = ChatRequest.aliveAfter(now());
		boolean received = direction == ChatRequestDirection.RECEIVED;
		List<ChatRequest> requests = received ? chatRequestRepository.findReceived(userId, RECEIVED, aliveAfter)
				: chatRequestRepository.findSent(userId, SENT, aliveAfter);

		Set<Long> counterpartIds = new HashSet<>();
		requests.forEach(request -> counterpartIds.add(counterpartId(request, received)));
		Map<Long, UserDisplayResponse> displays = userDisplayService.getAll(counterpartIds);
		Map<Long, List<InterestTagResponse>> sharedInterests = interestTagService.getSharedTags(counterpartIds,
				userId);

		List<ChatRequestResponse> items = requests.stream()
				.map(request -> {
					Long counterpartId = counterpartId(request, received);
					return new ChatRequestResponse(request.getId(), displays.get(counterpartId), request.getMessage(),
							sharedInterests.getOrDefault(counterpartId, List.of()), request.getCreatedAt(),
							request.expiresAt());
				})
				.toList();
		return new ChatRequestListResponse(items);
	}

	/** 받은 요청 수와 보낸 요청 수. */
	public ChatRequestCountResponse count(Long userId) {
		Instant aliveAfter = ChatRequest.aliveAfter(now());
		return new ChatRequestCountResponse(chatRequestRepository.countReceived(userId, RECEIVED, aliveAfter),
				chatRequestRepository.countSent(userId, SENT, aliveAfter));
	}

	@Override
	public long countReceivedPending(Long userId) {
		return chatRequestRepository.countReceived(userId, RECEIVED, ChatRequest.aliveAfter(now()));
	}

	@Override
	public Optional<Long> findLatestRequesterId(Long userId) {
		return chatRequestRepository
				.findLatestSenderIds(userId, RECEIVED, ChatRequest.aliveAfter(now()), Limit.of(1))
				.stream()
				.findFirst();
	}

	private static Long counterpartId(ChatRequest request, boolean received) {
		return received ? request.getSenderId() : request.getReceiverId();
	}

	private Instant now() {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

}
