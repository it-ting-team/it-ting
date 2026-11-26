package com.itting.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 채팅 테스트가 함께 쓰는 값. 대화 요청 · 대화방 · 메시지를 DB에 바로 넣고 읽는다(테스트의 준비와 확인 단계라 API를 거치지 않는다).
 * 시각은 UTC로 넣는다. 계정 · 세션은 {@link CommunitySteps}를 쓴다.
 */
@Component
public class ChatSteps {

	private final JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	public ChatSteps(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** 대화 요청 한 줄. 보낸 시각을 정해 만료와 순서를 맞춘다. */
	public long insertRequest(long senderId, long receiverId, String message, String status, Instant createdAt,
			Long chatRoomId) {
		sync();
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO chat_request (sender_id, receiver_id, message, status, chat_room_id, created_at)
					VALUES (?, ?, ?, ?, ?, ?)
					""", new String[] { "id" });
			statement.setLong(1, senderId);
			statement.setLong(2, receiverId);
			statement.setString(3, message);
			statement.setString(4, status);
			statement.setObject(5, chatRoomId);
			statement.setObject(6, utc(createdAt));
			return statement;
		}, keyHolder);
		sync();
		return keyHolder.getKey().longValue();
	}

	/** 1:1 대화방과 두 사람의 참여자 줄. 두 사람 모두 나가지 않았다. */
	public long insertDirectRoom(long firstId, long secondId, Instant createdAt) {
		sync();
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO chat_room (type, created_at, updated_at) VALUES ('DIRECT', ?, ?)
					""", new String[] { "id" });
			statement.setObject(1, utc(createdAt));
			statement.setObject(2, utc(createdAt));
			return statement;
		}, keyHolder);
		long roomId = keyHolder.getKey().longValue();
		for (long userId : List.of(firstId, secondId)) {
			jdbcTemplate.update("INSERT INTO chat_room_member (chat_room_id, user_id, joined_at) VALUES (?, ?, ?)",
					roomId, userId, utc(createdAt));
		}
		sync();
		return roomId;
	}

	/** 대화방에서 나간 것으로 바꾼다. */
	public void leave(long roomId, long userId) {
		sync();
		jdbcTemplate.update("UPDATE chat_room_member SET left_at = UTC_TIMESTAMP(6) WHERE chat_room_id = ? AND user_id = ?",
				roomId, userId);
		sync();
	}

	/** 모임 대화방과 주최자의 참여자 줄. 모임을 만드는 API를 거치지 않은 모임({@link CommunitySteps#insertMeetup})에 붙인다. */
	public long insertMeetupRoom(long meetupId, long hostId, Instant createdAt) {
		sync();
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO chat_room (type, meetup_id, created_at, updated_at) VALUES ('MEETUP', ?, ?, ?)
					""", new String[] { "id" });
			statement.setLong(1, meetupId);
			statement.setObject(2, utc(createdAt));
			statement.setObject(3, utc(createdAt));
			return statement;
		}, keyHolder);
		long roomId = keyHolder.getKey().longValue();
		join(roomId, hostId, createdAt);
		return roomId;
	}

	/** 대화방에 들어온 사람의 참여자 줄. 안내 메시지는 넣지 않는다. */
	public void join(long roomId, long userId, Instant joinedAt) {
		sync();
		jdbcTemplate.update("INSERT INTO chat_room_member (chat_room_id, user_id, joined_at) VALUES (?, ?, ?)", roomId,
				userId, utc(joinedAt));
		sync();
	}

	/** 사람이 보낸 메시지 한 줄. 넣은 순서대로 id가 커진다. */
	public long text(long roomId, long senderId, String content, Instant createdAt) {
		return insertMessage(roomId, "TEXT", senderId, content, createdAt);
	}

	/** 메시지 한 줄. {@code type}은 {@code TEXT}, {@code ACCEPTED}, {@code JOINED}, {@code LEFT}, {@code WITHDRAWN}이다. */
	public long insertMessage(long roomId, String type, long senderId, String content, Instant createdAt) {
		sync();
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO chat_message (chat_room_id, type, sender_id, content, created_at) VALUES (?, ?, ?, ?, ?)
					""", new String[] { "id" });
			statement.setLong(1, roomId);
			statement.setString(2, type);
			statement.setLong(3, senderId);
			statement.setString(4, content);
			statement.setObject(5, utc(createdAt));
			return statement;
		}, keyHolder);
		sync();
		return keyHolder.getKey().longValue();
	}

	/** 메시지에 사진을 붙인다. */
	public void attachImage(long messageId, int sortOrder, long imageId) {
		sync();
		jdbcTemplate.update("INSERT INTO chat_message_image (chat_message_id, sort_order, image_id) VALUES (?, ?, ?)",
				messageId, sortOrder, imageId);
		sync();
	}

	/** 메시지에 붙은 사진의 id. 보여 주는 순서대로. */
	public List<Long> imageIdsOf(long messageId) {
		sync();
		return jdbcTemplate.queryForList(
				"SELECT image_id FROM chat_message_image WHERE chat_message_id = ? ORDER BY sort_order", Long.class,
				messageId);
	}

	/** 이 사람의 읽은 자리를 정한다. null이면 아무것도 읽지 않았다. */
	public void setLastRead(long roomId, long userId, Long messageId) {
		sync();
		jdbcTemplate.update(
				"UPDATE chat_room_member SET last_read_message_id = ? WHERE chat_room_id = ? AND user_id = ?",
				messageId, roomId, userId);
		sync();
	}

	/** 이 사람의 읽은 자리. 아무것도 읽지 않았으면 null. */
	public Long lastRead(long roomId, long userId) {
		sync();
		return jdbcTemplate.queryForObject(
				"SELECT last_read_message_id FROM chat_room_member WHERE chat_room_id = ? AND user_id = ?", Long.class,
				roomId, userId);
	}

	/** 이 사람이 방에서 나간 시각. 나가지 않았으면 null. */
	public Object leftAt(long roomId, long userId) {
		sync();
		return jdbcTemplate.queryForMap(
				"SELECT left_at FROM chat_room_member WHERE chat_room_id = ? AND user_id = ?", roomId, userId)
				.get("left_at");
	}

	public void setNotice(long roomId, String notice) {
		sync();
		jdbcTemplate.update("UPDATE chat_room SET notice = ? WHERE id = ?", notice, roomId);
		sync();
	}

	/** 두 사람의 요청을 모두. 오래된 것부터. */
	public List<Map<String, Object>> requestsBetween(long firstId, long secondId) {
		sync();
		return jdbcTemplate.queryForList("""
				SELECT * FROM chat_request
				WHERE (sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)
				ORDER BY id
				""", firstId, secondId, secondId, firstId);
	}

	public Map<String, Object> request(long chatRequestId) {
		sync();
		return jdbcTemplate.queryForMap("SELECT * FROM chat_request WHERE id = ?", chatRequestId);
	}

	public String requestStatus(long chatRequestId) {
		sync();
		return jdbcTemplate.queryForObject("SELECT status FROM chat_request WHERE id = ?", String.class,
				chatRequestId);
	}

	public int roomCount() {
		sync();
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_room", Integer.class);
	}

	/** 이 사람이 들어 있는 대화방의 id. 오래된 것부터. */
	public List<Long> roomIdsOf(long userId) {
		sync();
		return jdbcTemplate.queryForList(
				"SELECT chat_room_id FROM chat_room_member WHERE user_id = ? ORDER BY chat_room_id", Long.class,
				userId);
	}

	public Map<String, Object> room(long roomId) {
		sync();
		return jdbcTemplate.queryForMap("SELECT * FROM chat_room WHERE id = ?", roomId);
	}

	/** 방의 참여자 줄. 사용자 id 순서. */
	public List<Map<String, Object>> members(long roomId) {
		sync();
		return jdbcTemplate.queryForList("SELECT * FROM chat_room_member WHERE chat_room_id = ? ORDER BY user_id",
				roomId);
	}

	/** 방의 메시지. 오래된 것부터. */
	public List<Map<String, Object>> messages(long roomId) {
		sync();
		return jdbcTemplate.queryForList("SELECT * FROM chat_message WHERE chat_room_id = ? ORDER BY id", roomId);
	}

	/** 이 사람이 받은 알림. 오래된 것부터. */
	public List<Map<String, Object>> notifications(long userId) {
		sync();
		return jdbcTemplate.queryForList("SELECT * FROM notification WHERE user_id = ? ORDER BY id", userId);
	}

	/** 이 사람이 받은 알림의 종류만. */
	public List<String> notificationTypes(long userId) {
		sync();
		return jdbcTemplate.queryForList("SELECT type FROM notification WHERE user_id = ? ORDER BY id", String.class,
				userId);
	}

	/**
	 * 이 사람들이 만든 채팅 줄(요청 · 대화방 · 참여자 · 메시지 · 메시지의 사진)과 알림만 지운다. 계정은 남긴다. 테스트 트랜잭션을 쓰지 않는(커밋하는)
	 * 테스트가 여러 번 겨룰 때 사이마다 부른다. 외래키 순서대로 지운다.
	 */
	public void deleteChatOf(long... userIds) {
		String in = inList(userIds);
		List<Long> roomIds = jdbcTemplate.queryForList(
				"SELECT DISTINCT chat_room_id FROM chat_room_member WHERE user_id IN (" + in + ")", Long.class);
		jdbcTemplate.update("DELETE FROM notification WHERE user_id IN (" + in + ") OR actor_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM chat_request WHERE sender_id IN (" + in + ") OR receiver_id IN (" + in + ")");
		for (Long roomId : roomIds) {
			jdbcTemplate.update("""
					DELETE FROM chat_message_image
					WHERE chat_message_id IN (SELECT id FROM chat_message WHERE chat_room_id = ?)
					""", roomId);
			jdbcTemplate.update("DELETE FROM chat_message WHERE chat_room_id = ?", roomId);
			jdbcTemplate.update("DELETE FROM chat_room_member WHERE chat_room_id = ?", roomId);
			jdbcTemplate.update("DELETE FROM chat_room WHERE id = ?", roomId);
		}
	}

	/** 이 사람들이 만든 채팅 줄 · 알림 · 올린 사진 · 계정을 지운다. 커밋하는 테스트가 끝에 부른다. */
	public void deleteAllOf(long... userIds) {
		deleteChatOf(userIds);
		String in = inList(userIds);
		jdbcTemplate.update("DELETE FROM uploaded_image WHERE uploader_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM user_interest WHERE user_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM user_profile WHERE user_id IN (" + in + ")");
		jdbcTemplate.update("DELETE FROM `user` WHERE id IN (" + in + ")");
	}

	private static String inList(long... ids) {
		List<String> values = new ArrayList<>();
		for (long id : ids) {
			values.add(String.valueOf(id));
		}
		return String.join(",", values);
	}

	/**
	 * 테스트 트랜잭션 안에서는 API 호출들이 영속성 컨텍스트를 함께 쓴다. API가 JPA로 미뤄 둔 변경을 SQL이 보도록 DB에 쓰고,
	 * SQL로 바꾼 값이 JPA에 보이도록 비운다. 트랜잭션이 없는(커밋하는) 테스트에서는 할 일이 없다.
	 */
	public void sync() {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			entityManager.flush();
			entityManager.clear();
		}
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
