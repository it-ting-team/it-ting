package com.itting.chat.repository;

import java.util.List;

import com.itting.chat.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

	/**
	 * 두 사람이 모두 나가지 않은 1:1 대화방의 id. 한 사람이라도 나갔으면(읽기 전용이 됐으면) 열린 방이 아니다. 대화 요청은
	 * 두 사람이 살아 있는 요청이 없을 때만 보내므로 보통 하나이고, 없으면 비어 있다.
	 */
	@Query("""
			SELECT r.id FROM ChatRoom r
			WHERE r.type = com.itting.chat.domain.ChatRoomType.DIRECT
			  AND EXISTS (SELECT 1 FROM ChatRoomMember m
			              WHERE m.chatRoomId = r.id AND m.userId = :userId AND m.leftAt IS NULL)
			  AND EXISTS (SELECT 1 FROM ChatRoomMember m
			              WHERE m.chatRoomId = r.id AND m.userId = :otherId AND m.leftAt IS NULL)
			ORDER BY r.id DESC
			""")
	List<Long> findOpenDirectRoomIds(@Param("userId") Long userId, @Param("otherId") Long otherId);

}
