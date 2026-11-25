package com.itting.chat.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.itting.chat.domain.ChatRequest;
import com.itting.chat.domain.ChatRequestStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 살아 있는 요청의 조건은 모두 {@code aliveAfter}다. 보낸 시각이 이 시각보다 늦어야 살아 있다
 * ({@link ChatRequest#aliveAfter(Instant)}).
 */
public interface ChatRequestRepository extends JpaRepository<ChatRequest, Long> {

	/**
	 * 요청의 보낸 사람과 받은 사람. 엔티티를 읽지 않으므로, 두 사람을 잠근 뒤 {@code findById}로 읽는 요청이 오래된 값이 아니다.
	 */
	@Query("SELECT new com.itting.chat.repository.ChatRequestParties(r.senderId, r.receiverId) "
			+ "FROM ChatRequest r WHERE r.id = :id")
	Optional<ChatRequestParties> findParties(@Param("id") Long id);

	/** 한 방향의 두 사람 사이에 살아 있는 요청. 두 사람 사이에는 하나뿐이다. */
	@Query("""
			SELECT r FROM ChatRequest r
			WHERE r.senderId = :senderId AND r.receiverId = :receiverId
			  AND r.status IN :statuses AND r.createdAt > :aliveAfter
			ORDER BY r.id DESC
			""")
	List<ChatRequest> findAlive(@Param("senderId") Long senderId, @Param("receiverId") Long receiverId,
			@Param("statuses") Collection<ChatRequestStatus> statuses, @Param("aliveAfter") Instant aliveAfter);

	/** 내가 받은 요청 가운데 살아 있는 것을 새것부터. */
	@Query("""
			SELECT r FROM ChatRequest r
			WHERE r.receiverId = :userId AND r.status IN :statuses AND r.createdAt > :aliveAfter
			ORDER BY r.createdAt DESC, r.id DESC
			""")
	List<ChatRequest> findReceived(@Param("userId") Long userId,
			@Param("statuses") Collection<ChatRequestStatus> statuses, @Param("aliveAfter") Instant aliveAfter);

	/** 내가 보낸 요청 가운데 살아 있는 것을 새것부터. */
	@Query("""
			SELECT r FROM ChatRequest r
			WHERE r.senderId = :userId AND r.status IN :statuses AND r.createdAt > :aliveAfter
			ORDER BY r.createdAt DESC, r.id DESC
			""")
	List<ChatRequest> findSent(@Param("userId") Long userId,
			@Param("statuses") Collection<ChatRequestStatus> statuses, @Param("aliveAfter") Instant aliveAfter);

	@Query("""
			SELECT COUNT(r) FROM ChatRequest r
			WHERE r.receiverId = :userId AND r.status IN :statuses AND r.createdAt > :aliveAfter
			""")
	long countReceived(@Param("userId") Long userId, @Param("statuses") Collection<ChatRequestStatus> statuses,
			@Param("aliveAfter") Instant aliveAfter);

	@Query("""
			SELECT COUNT(r) FROM ChatRequest r
			WHERE r.senderId = :userId AND r.status IN :statuses AND r.createdAt > :aliveAfter
			""")
	long countSent(@Param("userId") Long userId, @Param("statuses") Collection<ChatRequestStatus> statuses,
			@Param("aliveAfter") Instant aliveAfter);

	/** 내가 받은 요청 가운데 가장 최근에 보낸 사람. 요청이 없으면 비어 있다. */
	@Query("""
			SELECT r.senderId FROM ChatRequest r
			WHERE r.receiverId = :userId AND r.status IN :statuses AND r.createdAt > :aliveAfter
			ORDER BY r.createdAt DESC, r.id DESC
			""")
	List<Long> findLatestSenderIds(@Param("userId") Long userId,
			@Param("statuses") Collection<ChatRequestStatus> statuses, @Param("aliveAfter") Instant aliveAfter,
			Limit limit);

}
