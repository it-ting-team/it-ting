package com.itting.chat.repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

	public record RoomReadState(Long roomId, long unreadCount) {
	}

}
