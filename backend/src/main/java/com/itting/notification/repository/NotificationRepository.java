package com.itting.notification.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.itting.notification.domain.Notification;
import com.itting.notification.domain.NotificationType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 알림. 모든 조회는 {@code from}(30일 전) 뒤에 생긴 알림만 본다.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/** 내가 받은, {@code from} 뒤에 생긴 알림 하나. */
	Optional<Notification> findByIdAndUserIdAndCreatedAtGreaterThanEqual(Long id, Long userId, Instant from);

	/** 알림 팝업의 최근 알림. 안 읽은 것 먼저, 그 안에서 새것 먼저. */
	@Query("""
			SELECT n FROM Notification n
			WHERE n.userId = :userId AND n.createdAt >= :from AND n.type NOT IN :excluded
			ORDER BY CASE WHEN n.readAt IS NULL THEN 0 ELSE 1 END, n.createdAt DESC, n.id DESC
			""")
	List<Notification> findRecent(@Param("userId") Long userId, @Param("from") Instant from,
			@Param("excluded") Collection<NotificationType> excluded, Limit limit);

	/**
	 * 알림 목록의 앞 구간: {@code asOf} 때 안 읽었던 알림. ({@code beforeCreatedAt}, {@code beforeId})보다 뒤에 오는 것부터
	 * 새것 먼저 준다.
	 */
	@Query("""
			SELECT n FROM Notification n
			WHERE n.userId = :userId AND n.type IN :types
			  AND n.createdAt >= :from AND n.createdAt <= :asOf
			  AND (n.readAt IS NULL OR n.readAt > :asOf)
			  AND (n.createdAt < :beforeCreatedAt OR (n.createdAt = :beforeCreatedAt AND n.id < :beforeId))
			ORDER BY n.createdAt DESC, n.id DESC
			""")
	List<Notification> findUnreadPage(@Param("userId") Long userId, @Param("types") Collection<NotificationType> types,
			@Param("from") Instant from, @Param("asOf") Instant asOf,
			@Param("beforeCreatedAt") Instant beforeCreatedAt, @Param("beforeId") Long beforeId, Limit limit);

	/** 알림 목록의 뒤 구간: {@code asOf} 때 이미 읽었던 알림. 나머지는 {@link #findUnreadPage}와 같다. */
	@Query("""
			SELECT n FROM Notification n
			WHERE n.userId = :userId AND n.type IN :types
			  AND n.createdAt >= :from AND n.createdAt <= :asOf
			  AND n.readAt <= :asOf
			  AND (n.createdAt < :beforeCreatedAt OR (n.createdAt = :beforeCreatedAt AND n.id < :beforeId))
			ORDER BY n.createdAt DESC, n.id DESC
			""")
	List<Notification> findReadPage(@Param("userId") Long userId, @Param("types") Collection<NotificationType> types,
			@Param("from") Instant from, @Param("asOf") Instant asOf,
			@Param("beforeCreatedAt") Instant beforeCreatedAt, @Param("beforeId") Long beforeId, Limit limit);

	/** 안 읽은 알림 수. */
	long countByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(Long userId, Instant from);

	/** 이 종류 가운데 안 읽은 알림이 있는지. */
	boolean existsByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqualAndTypeIn(Long userId, Instant from,
			Collection<NotificationType> types);

	Optional<Notification> findByUserIdAndEventKey(Long userId, String eventKey);

	/**
	 * 내 안 읽은 알림을 모두 읽음으로 바꾼다. 새 메시지 알림은 키를 비운다({@link Notification#markRead}와 같다).
	 *
	 * @return 바꾼 줄 수
	 */
	@Modifying(flushAutomatically = true)
	@Query("""
			UPDATE Notification n
			SET n.readAt = :now, n.eventKey = CASE WHEN n.type = :chatType THEN NULL ELSE n.eventKey END
			WHERE n.userId = :userId AND n.readAt IS NULL
			""")
	int markAllRead(@Param("userId") Long userId, @Param("now") Instant now,
			@Param("chatType") NotificationType chatType);

	/**
	 * 이 대화방의 안 읽은 알림을 읽음으로 바꾸고 키를 비운다.
	 *
	 * @return 바꾼 줄 수
	 */
	@Modifying(flushAutomatically = true)
	@Query("""
			UPDATE Notification n SET n.readAt = :now, n.eventKey = NULL
			WHERE n.userId = :userId AND n.type = :type AND n.targetId = :chatRoomId AND n.readAt IS NULL
			""")
	int markChatRoomRead(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId,
			@Param("type") NotificationType type, @Param("now") Instant now);

	/**
	 * 키가 없으면 알림을 넣고, 있으면 아무것도 하지 않는다. 동시에 불려도 UNIQUE(user_id, event_key)로 한 줄만 생긴다.
	 *
	 * @return 넣었으면 1, 이미 있었으면 0
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			INSERT INTO notification (user_id, type, target_type, target_id, actor_id, created_at, event_key)
			VALUES (:userId, :type, :targetType, :targetId, :actorId, :now, :eventKey)
			ON DUPLICATE KEY UPDATE id = id
			""", nativeQuery = true)
	int insertIfAbsent(@Param("userId") Long userId, @Param("type") String type,
			@Param("targetType") String targetType, @Param("targetId") Long targetId, @Param("actorId") Long actorId,
			@Param("now") Instant now, @Param("eventKey") String eventKey);

	/**
	 * 키가 없으면 알림을 넣고, 있으면 그 줄의 시각과 일으킨 사람만 바꾼다. 새 메시지 알림을 대화방마다 한 줄로 묶는다.
	 *
	 * @return 넣었으면 1, 바꿨으면 2(MySQL이 돌려주는 값)
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = """
			INSERT INTO notification (user_id, type, target_type, target_id, actor_id, created_at, event_key)
			VALUES (:userId, :type, :targetType, :targetId, :actorId, :now, :eventKey) AS incoming
			ON DUPLICATE KEY UPDATE created_at = incoming.created_at, actor_id = incoming.actor_id
			""", nativeQuery = true)
	int upsert(@Param("userId") Long userId, @Param("type") String type, @Param("targetType") String targetType,
			@Param("targetId") Long targetId, @Param("actorId") Long actorId, @Param("now") Instant now,
			@Param("eventKey") String eventKey);

	/**
	 * {@code cutoff}보다 먼저 생긴 알림을 {@code batchSize}줄까지 지운다.
	 *
	 * @return 지운 줄 수
	 */
	@Modifying
	@Query(value = "DELETE FROM notification WHERE created_at < :cutoff LIMIT :batchSize", nativeQuery = true)
	int deleteCreatedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

	/** 이 사람이 받은 알림을 모두 지운다(회원 탈퇴). 이 사람이 일으킨 남의 알림({@code actor_id})은 남긴다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM Notification n WHERE n.userId = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);

}
