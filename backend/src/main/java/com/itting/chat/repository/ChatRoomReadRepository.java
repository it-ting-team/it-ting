package com.itting.chat.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.itting.chat.domain.ChatRoomType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChatRoomReadRepository {

	private static final String UNREAD_COUNT = """
			(SELECT COUNT(*) FROM chat_message cm
			 WHERE cm.chat_room_id = cr.id AND cm.type = 'TEXT' AND cm.sender_id <> :userId
			   AND cm.id > COALESCE(crm.last_read_message_id, 0))
			""";

	private static final String JOINED_ROOMS = """
			FROM chat_room cr
			JOIN chat_room_member crm ON crm.chat_room_id = cr.id
			LEFT JOIN meetup m ON m.id = cr.meetup_id
			WHERE crm.user_id = :userId AND crm.left_at IS NULL
			  AND (cr.type = 'DIRECT' OR (m.id IS NOT NULL AND m.deleted_at IS NULL))
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public ChatRoomReadRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 모임 ID로 묶은 접근 가능한 대화방과 안 읽은 수. */
	public Map<Long, RoomReadState> findForMeetups(Long userId, Collection<Long> meetupIds) {
		if (meetupIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.query("SELECT cr.meetup_id, cr.id, " + UNREAD_COUNT + " AS unread_count "
				+ JOINED_ROOMS + " AND cr.meetup_id IN (:ids)", Map.of("userId", userId, "ids", meetupIds), rows -> {
					Map<Long, RoomReadState> results = new HashMap<>();
					while (rows.next()) {
						results.put(rows.getLong("meetup_id"),
								new RoomReadState(rows.getLong("id"), rows.getLong("unread_count")));
					}
					return results;
				});
	}

	/** 안 읽은 수가 필요 없는 모임 조회에서 방 ID만 읽는다. */
	public Map<Long, Long> findJoinedMeetupRoomIds(Long userId, Collection<Long> meetupIds) {
		if (meetupIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.query("SELECT cr.meetup_id, cr.id " + JOINED_ROOMS + " AND cr.meetup_id IN (:ids)",
				Map.of("userId", userId, "ids", meetupIds), rows -> {
					Map<Long, Long> rooms = new HashMap<>();
					while (rows.next()) {
						rooms.put(rows.getLong("meetup_id"), rows.getLong("id"));
					}
					return rooms;
				});
	}

	/** 접근 가능한 방만 방 ID별로 센다. 안 읽은 메시지가 없는 방에는 0을 담는다. */
	public Map<Long, Long> countUnreadByRoomIds(Long userId, Collection<Long> roomIds) {
		if (roomIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.query("SELECT cr.id, " + UNREAD_COUNT + " AS unread_count " + JOINED_ROOMS
				+ " AND cr.id IN (:ids)", Map.of("userId", userId, "ids", roomIds), rows -> {
					Map<Long, Long> counts = new HashMap<>();
					while (rows.next()) {
						counts.put(rows.getLong("id"), rows.getLong("unread_count"));
					}
					return counts;
				});
	}

	/**
	 * 내가 들어갈 수 있는 대화방을 모두 읽는다(대화 목록). 내가 나간 방과 삭제된 모임의 방은 없다. 순서는 정해 주지 않는다.
	 * 방마다 마지막 메시지의 id, 안 읽은 수, 1:1이면 상대와 상대가 나갔는지를 함께 담는다.
	 */
	public List<JoinedRoom> findJoinedRooms(Long userId) {
		return jdbc.query("""
				SELECT cr.id, cr.type, cr.meetup_id, m.title AS meetup_title, cr.created_at,
				       (SELECT MAX(lm.id) FROM chat_message lm WHERE lm.chat_room_id = cr.id) AS last_message_id,
				       (SELECT o.user_id FROM chat_room_member o
				        WHERE o.chat_room_id = cr.id AND o.user_id <> :userId AND cr.type = 'DIRECT') AS counterpart_id,
				       (SELECT o.left_at IS NOT NULL FROM chat_room_member o
				        WHERE o.chat_room_id = cr.id AND o.user_id <> :userId AND cr.type = 'DIRECT') AS counterpart_left,
				""" + UNREAD_COUNT + " AS unread_count " + JOINED_ROOMS, Map.of("userId", userId), (rs, row) -> {
					Long meetupId = rs.getObject("meetup_id", Long.class);
					return new JoinedRoom(rs.getLong("id"), ChatRoomType.valueOf(rs.getString("type")), meetupId,
							rs.getString("meetup_title"),
							rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
							rs.getObject("last_message_id", Long.class), rs.getObject("counterpart_id", Long.class),
							rs.getBoolean("counterpart_left"), rs.getLong("unread_count"));
				});
	}

	public record RoomReadState(Long roomId, long unreadCount) {
	}

	/**
	 * 대화 목록의 한 줄.
	 *
	 * @param meetupTitle 모임 대화방의 모임 이름. 1:1이면 null
	 * @param createdAt 방을 만든 시각. 메시지가 없는 방의 순서를 정한다
	 * @param lastMessageId 마지막 메시지. 메시지가 없으면 null
	 * @param counterpartId 1:1 대화의 상대. 모임이면 null
	 * @param counterpartLeft 1:1 대화의 상대가 방에서 나갔는지
	 */
	public record JoinedRoom(Long id, ChatRoomType type, Long meetupId, String meetupTitle, Instant createdAt,
			Long lastMessageId, Long counterpartId, boolean counterpartLeft, long unreadCount) {
	}

}
