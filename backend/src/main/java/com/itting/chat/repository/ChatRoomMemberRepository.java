package com.itting.chat.repository;

import java.util.List;
import java.util.Optional;

import com.itting.chat.domain.ChatRoomMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMember.Key> {

	/** 방에 들어온 적이 있는 모든 사람. 나간 사람도 담는다. */
	List<ChatRoomMember> findAllByChatRoomId(Long chatRoomId);

	/** 방에 지금 있는 사람의 계정 id. 나간 사람은 담지 않는다. */
	@Query("SELECT m.userId FROM ChatRoomMember m WHERE m.chatRoomId = :chatRoomId AND m.leftAt IS NULL")
	List<Long> findJoinedUserIds(@Param("chatRoomId") Long chatRoomId);

	/** 이 사람이 지금 들어 있는 대화방의 id. 나간 방은 담지 않는다. id 순서다. */
	@Query("SELECT m.chatRoomId FROM ChatRoomMember m WHERE m.userId = :userId AND m.leftAt IS NULL ORDER BY m.chatRoomId")
	List<Long> findJoinedRoomIds(@Param("userId") Long userId);

	/**
	 * 내 참여자 줄을 트랜잭션이 끝날 때까지 잠그고 읽는다. 같은 사람의 나가기가 동시에 두 번 들어와도 한 번만 처리되게 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT m FROM ChatRoomMember m WHERE m.chatRoomId = :chatRoomId AND m.userId = :userId")
	Optional<ChatRoomMember> findForUpdate(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);

	/**
	 * 읽은 자리를 이 메시지로 옮긴다. 이미 더 뒤까지 읽었으면 바꾸지 않는다. 비교와 쓰기를 한 SQL로 해서, 같은 사람의 요청이 겹쳐도
	 * 읽은 자리가 앞으로 돌아가지 않는다.
	 *
	 * <p>바로 실행하고 영속성 컨텍스트를 비운다. 앞서 읽은 엔티티가 떨어지므로 엔티티를 고치는 일은 이 앞에서 마친다.
	 *
	 * @return 옮겼으면 1, 이미 그 뒤까지 읽었으면 0
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			UPDATE ChatRoomMember m SET m.lastReadMessageId = :messageId
			WHERE m.chatRoomId = :chatRoomId AND m.userId = :userId
			  AND (m.lastReadMessageId IS NULL OR m.lastReadMessageId < :messageId)
			""")
	int advanceLastRead(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId,
			@Param("messageId") Long messageId);

}
