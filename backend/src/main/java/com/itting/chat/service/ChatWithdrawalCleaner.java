package com.itting.chat.service;

import java.time.Instant;
import java.util.List;

import com.itting.chat.domain.ChatMessage;
import com.itting.chat.domain.ChatRequest;
import com.itting.chat.domain.ChatRequestStatus;
import com.itting.chat.domain.ChatRoomMember;
import com.itting.chat.repository.ChatMessageRepository;
import com.itting.chat.repository.ChatRequestRepository;
import com.itting.chat.repository.ChatRoomMemberRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴의 채팅 영역 정리({@code docs/api/10-settings.md}의 "탈퇴할 때 하는 일"). 탈퇴 트랜잭션 안에서만 부른다.
 *
 * <p>탈퇴 트랜잭션이 먼저 탈퇴하는 사람의 {@code user} 줄을 잠근다. 대화 요청 · 수락 · 나가기와 1:1 메시지 보내기는 두 사람의
 * {@code user} 줄을 잠그므로, 탈퇴가 끝날 때까지 기다렸다가 탈퇴한 계정을 본다. 상대의 줄은 잠그지 않는다. 탈퇴하는 사람의 줄을
 * 이미 잡은 채로 id가 더 작은 상대를 잠그면, 두 줄을 id 순서로 잠그는 다른 트랜잭션과 서로 기다리게 된다.
 */
@Component
public class ChatWithdrawalCleaner {

	private final ChatRoomMemberRepository members;

	private final ChatMessageRepository messages;

	private final ChatRequestRepository requests;

	private final ChatEventPublisher events;

	public ChatWithdrawalCleaner(ChatRoomMemberRepository members, ChatMessageRepository messages,
			ChatRequestRepository requests, ChatEventPublisher events) {
		this.members = members;
		this.messages = messages;
		this.requests = requests;
		this.events = events;
	}

	/**
	 * 들어 있는 대화방(1:1, 모임)마다 {@code WITHDRAWN} 안내 메시지를 넣고 나간 것으로 한다. 상대의 1:1 대화방은 읽기 전용이 된다.
	 * 보내거나 받은 대화 요청은 모두 지운다. 실시간 소식(대화방의 새 메시지, 내 요청을 받아 둔 사람의 요청 수)은 커밋된 뒤에 간다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void clean(Long userId, Instant now) {
		for (Long chatRoomId : members.findJoinedRoomIds(userId)) {
			members.findForUpdate(chatRoomId, userId)
					.filter(ChatRoomMember::isJoined)
					.ifPresent(member -> leave(member, now));
		}
		List<Long> waitingReceivers = requests
				.findSent(userId, List.of(ChatRequestStatus.PENDING), ChatRequest.aliveAfter(now)).stream()
				.map(ChatRequest::getReceiverId)
				.distinct()
				.toList();
		requests.deleteAllByParty(userId);
		waitingReceivers.forEach(events::requestCountChanged);
	}

	private void leave(ChatRoomMember member, Instant now) {
		member.leave(now);
		members.saveAndFlush(member);
		ChatMessage message = messages.save(ChatMessage.withdrawn(member.getChatRoomId(), member.getUserId()));
		events.messageCreated(member.getChatRoomId(), message.getId());
	}

}
