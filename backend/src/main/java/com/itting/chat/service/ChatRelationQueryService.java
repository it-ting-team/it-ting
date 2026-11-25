package com.itting.chat.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.itting.burrow.dto.BurrowVisitResponse.ChatResponse;
import com.itting.burrow.dto.BurrowVisitResponse.ChatStatus;
import com.itting.burrow.service.ChatRelationReader;
import com.itting.chat.domain.ChatRequest;
import com.itting.chat.domain.ChatRequestStatus;
import com.itting.chat.repository.ChatRequestRepository;
import com.itting.chat.repository.ChatRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 두 사람 사이의 대화 버튼 상태. 대화 요청 보내기({@link ChatRequestService})와 같은 기준으로 위에서부터 본다.
 * <ol>
 * <li>두 사람 모두 나가지 않은 1:1 대화방이 있으면 {@code CHATTING}. 요청이 남아 있어도 그렇다.</li>
 * <li>내가 보낸 살아 있는 요청이 있으면 {@code REQUEST_SENT}. 상대가 거절해도 만료될 때까지 그렇다.</li>
 * <li>상대가 보낸 요청이 {@code PENDING}이고 살아 있으면 {@code REQUEST_RECEIVED}. 내가 거절한 요청은 보지 않는다.</li>
 * <li>아니면 {@code NONE}.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class ChatRelationQueryService implements ChatRelationReader {

	private static final List<ChatRequestStatus> SENT = List.of(ChatRequestStatus.PENDING, ChatRequestStatus.REJECTED);

	private static final List<ChatRequestStatus> RECEIVED = List.of(ChatRequestStatus.PENDING);

	private final ChatRoomRepository chatRoomRepository;

	private final ChatRequestRepository chatRequestRepository;

	private final Clock clock;

	public ChatRelationQueryService(ChatRoomRepository chatRoomRepository,
			ChatRequestRepository chatRequestRepository, Clock clock) {
		this.chatRoomRepository = chatRoomRepository;
		this.chatRequestRepository = chatRequestRepository;
		this.clock = clock;
	}

	@Override
	public ChatResponse read(Long viewerId, Long ownerId) {
		List<Long> openRooms = chatRoomRepository.findOpenDirectRoomIds(viewerId, ownerId);
		if (!openRooms.isEmpty()) {
			return new ChatResponse(ChatStatus.CHATTING, openRooms.getFirst());
		}
		Instant aliveAfter = ChatRequest.aliveAfter(Instant.now(clock));
		if (!chatRequestRepository.findAlive(viewerId, ownerId, SENT, aliveAfter).isEmpty()) {
			return new ChatResponse(ChatStatus.REQUEST_SENT, null);
		}
		if (!chatRequestRepository.findAlive(ownerId, viewerId, RECEIVED, aliveAfter).isEmpty()) {
			return new ChatResponse(ChatStatus.REQUEST_RECEIVED, null);
		}
		return new ChatResponse(ChatStatus.NONE, null);
	}

}
