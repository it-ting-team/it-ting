package com.itting.community.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itting.community.domain.PostStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 댓글 목록을 읽는 쿼리. 한 번에 여러 댓글의 본문 · 좋아요를 모아 읽으므로 SQL로 쓴다.
 * 시각은 UTC의 날짜 · 시각으로 주고받는다(DB에 UTC로 저장되어 있다).
 */
@Repository
public class CommentQueryRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public CommentQueryRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 목록에 나오는 댓글 한 줄(본문 제외).
	 *
	 * @param parentId 답글이 속한 맨 위 댓글. 댓글이면 null
	 * @param mentionUserId 답글에 다시 답했을 때 그 답글의 작성자. 없으면 null
	 */
	public record CommentRow(Long id, Long authorId, Long parentId, Long mentionUserId, PostStatus status,
			Instant createdAt) {
	}

	/**
	 * 지금 보이는 본문과 그 버전 번호.
	 *
	 * @param version 2 이상이면 고친 댓글이다
	 */
	public record Body(String content, int version) {
	}

	/**
	 * 글의 댓글(답글이 아닌 것) 가운데 삭제되지 않은 것을 등록한 순서(쓴 시각, 같으면 id)로 준다.
	 *
	 * @param afterCreatedAt 이 댓글 뒤부터 준다. 처음이면 null
	 * @param afterId {@code afterCreatedAt}과 함께 마지막으로 준 댓글의 id
	 */
	public List<CommentRow> findTopLevel(Long postId, Instant afterCreatedAt, Long afterId, int limit) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("postId", postId)
				.addValue("limit", limit);
		StringBuilder sql = new StringBuilder("""
				SELECT c.id, c.author_id, c.parent_id, c.mention_user_id, c.status, c.created_at
				FROM comment c
				WHERE c.post_id = :postId AND c.parent_id IS NULL AND c.status <> 'DELETED'
				""");
		if (afterCreatedAt != null) {
			sql.append(" AND (c.created_at > :afterCreatedAt OR (c.created_at = :afterCreatedAt AND c.id > :afterId))");
			params.addValue("afterCreatedAt", utc(afterCreatedAt)).addValue("afterId", afterId);
		}
		sql.append(" ORDER BY c.created_at, c.id LIMIT :limit");
		return jdbc.query(sql.toString(), params, (rs, rowNum) -> row(rs));
	}

	/** 이 댓글들에 달린 답글 가운데 삭제되지 않은 것을 등록한 순서로 준다. */
	public List<CommentRow> findReplies(Collection<Long> parentIds) {
		if (parentIds.isEmpty()) {
			return List.of();
		}
		return jdbc.query("""
				SELECT c.id, c.author_id, c.parent_id, c.mention_user_id, c.status, c.created_at
				FROM comment c
				WHERE c.parent_id IN (:parentIds) AND c.status <> 'DELETED'
				ORDER BY c.created_at, c.id
				""", Map.of("parentIds", parentIds), (rs, rowNum) -> row(rs));
	}

	/** @return 댓글 id → 지금 보이는 본문. 본문이 없는 댓글은 담지 않는다 */
	public Map<Long, Body> findBodies(Collection<Long> commentIds) {
		Map<Long, Body> result = new HashMap<>();
		if (commentIds.isEmpty()) {
			return result;
		}
		jdbc.query("""
				SELECT v.comment_id, v.content, v.version FROM comment_version v
				WHERE v.comment_id IN (:ids)
				  AND v.version = (SELECT MAX(v2.version) FROM comment_version v2 WHERE v2.comment_id = v.comment_id)
				""", Map.of("ids", commentIds), rs -> {
			result.put(rs.getLong("comment_id"), new Body(rs.getString("content"), rs.getInt("version")));
		});
		return result;
	}

	/** @return 댓글 id → 좋아요 수. 좋아요가 없는 댓글은 담지 않는다 */
	public Map<Long, Long> countLikes(Collection<Long> commentIds) {
		Map<Long, Long> result = new HashMap<>();
		if (commentIds.isEmpty()) {
			return result;
		}
		jdbc.query("""
				SELECT comment_id, COUNT(*) AS cnt FROM comment_like WHERE comment_id IN (:ids) GROUP BY comment_id
				""", Map.of("ids", commentIds), rs -> {
			result.put(rs.getLong("comment_id"), rs.getLong("cnt"));
		});
		return result;
	}

	/** 이 댓글들 가운데 이 계정이 좋아요를 누른 것. */
	public Set<Long> findLiked(Long userId, Collection<Long> commentIds) {
		if (commentIds.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(jdbc.queryForList(
				"SELECT comment_id FROM comment_like WHERE user_id = :userId AND comment_id IN (:ids)",
				Map.of("userId", userId, "ids", commentIds), Long.class));
	}

	private static CommentRow row(ResultSet rs) throws SQLException {
		return new CommentRow(rs.getLong("id"), rs.getLong("author_id"), rs.getObject("parent_id", Long.class),
				rs.getObject("mention_user_id", Long.class), PostStatus.valueOf(rs.getString("status")),
				rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
