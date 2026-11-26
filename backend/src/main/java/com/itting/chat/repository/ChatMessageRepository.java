package com.itting.chat.repository;

import java.util.List;

import com.itting.chat.domain.ChatMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	/** 방의 메시지를 새 것부터. 사람이 보낸 메시지와 안내 메시지를 함께 준다. */
	List<ChatMessage> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Limit limit);

	/** 이 메시지보다 오래된 방의 메시지를 새 것부터. */
	List<ChatMessage> findByChatRoomIdAndIdLessThanOrderByIdDesc(Long chatRoomId, Long id, Limit limit);

	/** 이 방의 메시지인지. */
	boolean existsByIdAndChatRoomId(Long id, Long chatRoomId);

}
