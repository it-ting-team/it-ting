package com.itting.community.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.itting.community.domain.PostCategory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 내 활동(내가 쓴 글, 댓글 단 글, 저장한 글)을 읽는 쿼리({@code docs/api/08-community.md}의 {@code GET /api/v1/me/posts}).
 * 삭제된 글은 어느 탭에도 없다. 시각은 UTC의 날짜 · 시각으로 주고받는다.
 *
 * <p>순서가 같으면 글 id가 큰 것이 먼저다.
 */
@Repository
public class MyPostQueryRepository {

	/** 내가 쓴 글. 가려진 글도 든다. */
	private static final String WRITTEN_FROM = """
			FROM post p WHERE p.author_id = :me AND p.status <> 'DELETED'
			""";

	/** 내 삭제되지 않은 댓글 · 답글이 달린 공개 글. 글 하나에 한 줄로 묶는다. */
	private static final String COMMENTED_FROM = """
			FROM post p
			JOIN comment c ON c.post_id = p.id AND c.author_id = :me AND c.status <> 'DELETED'
			WHERE p.status = 'PUBLIC'
			""";

	/** 내가 저장한 공개 글. */
	private static final String BOOKMARKED_FROM = """
			FROM post_bookmark b JOIN post p ON p.id = b.post_id
			WHERE b.user_id = :me AND p.status = 'PUBLIC'
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public MyPostQueryRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Page(List<Long> ids, long totalItems) {
	}

	/** 내가 쓴 글을 새로 쓴 글부터. */
	public Page findWritten(Long me, PostCategory category, int offset, int limit) {
		MapSqlParameterSource params = params(me, category, offset, limit);
		String where = WRITTEN_FROM + categoryFilter(category);
		return new Page(jdbc.queryForList("SELECT p.id " + where
				+ " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset", params, Long.class),
				count("SELECT COUNT(*) " + where, params));
	}

	/** 댓글 단 글을 내가 가장 최근에 댓글을 단 글부터. */
	public Page findCommented(Long me, PostCategory category, int offset, int limit) {
		MapSqlParameterSource params = params(me, category, offset, limit);
		String where = COMMENTED_FROM + categoryFilter(category);
		return new Page(jdbc.queryForList("SELECT p.id " + where
				+ " GROUP BY p.id ORDER BY MAX(c.created_at) DESC, p.id DESC LIMIT :limit OFFSET :offset", params,
				Long.class), count("SELECT COUNT(DISTINCT p.id) " + where, params));
	}

	/** 저장한 글을 최근에 저장한 글부터. */
	public Page findBookmarked(Long me, PostCategory category, int offset, int limit) {
		MapSqlParameterSource params = params(me, category, offset, limit);
		String where = BOOKMARKED_FROM + categoryFilter(category);
		return new Page(jdbc.queryForList("SELECT p.id " + where
				+ " ORDER BY b.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset", params, Long.class),
				count("SELECT COUNT(*) " + where, params));
	}

	/** 카테고리로 거르지 않은 세 탭의 글 수. */
	public record Summary(long writtenCount, long commentedCount, long bookmarkedCount) {
	}

	public Summary summarize(Long me) {
		MapSqlParameterSource params = new MapSqlParameterSource("me", me);
		return new Summary(count("SELECT COUNT(*) " + WRITTEN_FROM, params),
				count("SELECT COUNT(DISTINCT p.id) " + COMMENTED_FROM, params),
				count("SELECT COUNT(*) " + BOOKMARKED_FROM, params));
	}

	/** @return 글 id → 내가 저장한 시각. 저장하지 않은 글은 담지 않는다 */
	public Map<Long, Instant> findBookmarkedAt(Long me, Collection<Long> postIds) {
		Map<Long, Instant> result = new HashMap<>();
		if (postIds.isEmpty()) {
			return result;
		}
		jdbc.query("SELECT post_id, created_at FROM post_bookmark WHERE user_id = :me AND post_id IN (:ids)",
				Map.of("me", me, "ids", postIds), rs -> {
					result.put(rs.getLong("post_id"), instant(rs, "created_at"));
				});
		return result;
	}

	/**
	 * 한 글에 내가 단 댓글 · 답글 가운데 가장 최근의 것과 그 수. 삭제된 것은 세지 않고, 가려진 것은 센다.
	 *
	 * @param latestCommentId 가장 최근에 쓴 것(같은 시각이면 id가 큰 것)
	 */
	public record MyComments(Long latestCommentId, long count) {
	}

	/** @return 글 id → 내 댓글. 내 댓글이 없는 글은 담지 않는다 */
	public Map<Long, MyComments> findMyComments(Long me, Collection<Long> postIds) {
		Map<Long, MyComments> result = new HashMap<>();
		if (postIds.isEmpty()) {
			return result;
		}
		jdbc.query("""
				SELECT c.post_id, COUNT(*) AS cnt,
				       (SELECT c2.id FROM comment c2
				        WHERE c2.post_id = c.post_id AND c2.author_id = :me AND c2.status <> 'DELETED'
				        ORDER BY c2.created_at DESC, c2.id DESC LIMIT 1) AS latest_id
				FROM comment c
				WHERE c.author_id = :me AND c.status <> 'DELETED' AND c.post_id IN (:ids)
				GROUP BY c.post_id
				""", Map.of("me", me, "ids", postIds), rs -> {
			result.put(rs.getLong("post_id"), new MyComments(rs.getLong("latest_id"), rs.getLong("cnt")));
		});
		return result;
	}

	private static MapSqlParameterSource params(Long me, PostCategory category, int offset, int limit) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("me", me)
				.addValue("offset", offset)
				.addValue("limit", limit);
		if (category != null) {
			params.addValue("category", category.name());
		}
		return params;
	}

	private static String categoryFilter(PostCategory category) {
		return category == null ? "" : " AND p.category = :category";
	}

	private long count(String sql, MapSqlParameterSource params) {
		Long count = jdbc.queryForObject(sql, params, Long.class);
		return count == null ? 0 : count;
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
	}

}
