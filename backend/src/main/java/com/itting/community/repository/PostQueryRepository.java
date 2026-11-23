package com.itting.community.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itting.community.domain.PostCategory;
import com.itting.community.domain.PostSort;
import com.itting.community.domain.PostStatus;
import com.itting.community.dto.PostDetailResponse;
import com.itting.community.dto.PostInterestCategoryResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 글을 읽는 쿼리. 거르기 · 정렬이 여러 갈래이고 여러 테이블의 수를 함께 세므로 SQL로 쓴다.
 * 시각은 UTC의 날짜 · 시각으로 주고받는다(DB에 UTC로 저장되어 있다).
 *
 * <p>인기 점수는 {@code since}(요청 시각의 168시간 전) 뒤에 눌린 좋아요의 수, 댓글 수는 삭제되지 않은 댓글과 답글의 수다.
 * 값이 같으면 새로 쓴 글이 먼저이고, 쓴 시각까지 같으면 id가 큰 글이 먼저다.
 */
@Repository
public class PostQueryRepository {

	/** 지금 보이는 버전(번호가 가장 큰 것)을 {@code v}로 잇는다. */
	private static final String JOIN_LATEST_VERSION = """
			JOIN post_version v ON v.post_id = p.id
			 AND v.version = (SELECT MAX(v2.version) FROM post_version v2 WHERE v2.post_id = p.id)
			""";

	private static final String JOIN_SCORE = """
			LEFT JOIN (SELECT post_id, COUNT(*) AS score FROM post_like WHERE created_at >= :since GROUP BY post_id) s
			 ON s.post_id = p.id
			""";

	private static final String JOIN_COMMENT_COUNT = """
			LEFT JOIN (SELECT post_id, COUNT(*) AS comment_count FROM comment WHERE status <> 'DELETED'
			           GROUP BY post_id) cc
			 ON cc.post_id = p.id
			""";

	/** LIKE에서 {@code %}, {@code _}를 글자 그대로 찾게 하는 이스케이프 문자. */
	private static final char LIKE_ESCAPE = '!';

	private final NamedParameterJdbcTemplate jdbc;

	public PostQueryRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 글 목록의 조건.
	 *
	 * @param category null이면 전체
	 * @param myInterestsOf 이 계정의 관심사 대분류가 태그에 있는 글만. null이면 거르지 않는다
	 * @param interestCategoryId 이 대분류의 태그가 달린 글만. null이면 거르지 않는다
	 * @param keyword 최신 제목 · 본문, 작성자 닉네임에서 찾을 글자. null이면 찾지 않는다
	 */
	public record Search(PostCategory category, Long myInterestsOf, Long interestCategoryId, String keyword) {
	}

	public record Page(List<Long> ids, long totalItems) {
	}

	/** 조건에 맞는 공개 글의 id를 정렬해 한 페이지 준다. */
	public Page search(Search search, PostSort sort, Instant since, int offset, int limit) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("since", utc(since))
				.addValue("offset", offset)
				.addValue("limit", limit);
		StringBuilder where = new StringBuilder("WHERE p.status = 'PUBLIC'");
		if (search.category() != null) {
			where.append(" AND p.category = :category");
			params.addValue("category", search.category().name());
		}
		if (search.myInterestsOf() != null) {
			where.append("""
					 AND EXISTS (SELECT 1 FROM post_interest pi
					             JOIN interest i ON i.interest_category_id = pi.interest_category_id
					             JOIN user_interest ui ON ui.interest_id = i.id
					             WHERE pi.post_id = p.id AND ui.user_id = :me)
					""");
			params.addValue("me", search.myInterestsOf());
		}
		if (search.interestCategoryId() != null) {
			where.append("""
					 AND EXISTS (SELECT 1 FROM post_interest pi
					             WHERE pi.post_id = p.id AND pi.interest_category_id = :interestCategoryId)
					""");
			params.addValue("interestCategoryId", search.interestCategoryId());
		}
		String from = "FROM post p ";
		if (search.keyword() != null) {
			from += JOIN_LATEST_VERSION + " LEFT JOIN user_profile up ON up.user_id = p.author_id ";
			where.append("""
					 AND (v.title LIKE :keyword ESCAPE '!' OR v.content LIKE :keyword ESCAPE '!'
					      OR up.nickname LIKE :keyword ESCAPE '!')
					""");
			params.addValue("keyword", "%" + escapeLike(search.keyword()) + "%");
		}
		Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + where, params, Long.class);
		String sortJoin = switch (sort) {
			case LATEST -> "";
			case POPULAR -> JOIN_SCORE;
			case MOST_COMMENTED -> JOIN_COMMENT_COUNT;
		};
		String orderBy = switch (sort) {
			case LATEST -> " ORDER BY p.created_at DESC, p.id DESC";
			case POPULAR -> " ORDER BY COALESCE(s.score, 0) DESC, p.created_at DESC, p.id DESC";
			case MOST_COMMENTED -> " ORDER BY COALESCE(cc.comment_count, 0) DESC, p.created_at DESC, p.id DESC";
		};
		List<Long> ids = jdbc.queryForList("SELECT p.id " + from + sortJoin + " " + where + orderBy
				+ " LIMIT :limit OFFSET :offset", params, Long.class);
		return new Page(ids, total == null ? 0 : total);
	}

	/**
	 * 인기 점수가 높은 공개 글.
	 *
	 * @param scoredOnly true면 점수가 0인 글을 뺀다
	 * @param excludePostId 뺄 글. null이면 빼지 않는다
	 * @param interestCategoryIds 이 대분류 가운데 하나라도 태그에 있는 글만. 비어 있으면 거르지 않는다
	 */
	public List<Long> findPopular(Instant since, int limit, boolean scoredOnly, Long excludePostId,
			Collection<Long> interestCategoryIds) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("since", utc(since))
				.addValue("limit", limit);
		StringBuilder sql = new StringBuilder("SELECT p.id FROM post p ")
				.append(JOIN_SCORE)
				.append(" WHERE p.status = 'PUBLIC'");
		if (scoredOnly) {
			sql.append(" AND s.score > 0");
		}
		if (excludePostId != null) {
			sql.append(" AND p.id <> :excludePostId");
			params.addValue("excludePostId", excludePostId);
		}
		if (!interestCategoryIds.isEmpty()) {
			sql.append("""
					 AND EXISTS (SELECT 1 FROM post_interest pi
					             WHERE pi.post_id = p.id AND pi.interest_category_id IN (:interestCategoryIds))
					""");
			params.addValue("interestCategoryIds", interestCategoryIds);
		}
		sql.append(" ORDER BY COALESCE(s.score, 0) DESC, p.created_at DESC, p.id DESC LIMIT :limit");
		return jdbc.queryForList(sql.toString(), params, Long.class);
	}

	/**
	 * {@code since} 뒤에 쓴 공개 글의 태그를 대분류별로 세어 글이 많은 것부터 준다. 같으면 id가 작은 것이 먼저다.
	 * 지운 대분류와 글이 없는 대분류는 주지 않는다.
	 */
	public List<PostInterestCategoryResponse> findTrendingInterestCategories(Instant since, int limit) {
		return jdbc.query("""
				SELECT ic.id, ic.name
				FROM post p
				JOIN post_interest pi ON pi.post_id = p.id
				JOIN interest_category ic ON ic.id = pi.interest_category_id
				WHERE p.status = 'PUBLIC' AND p.created_at >= :since AND ic.deleted_at IS NULL
				GROUP BY ic.id, ic.name
				ORDER BY COUNT(DISTINCT p.id) DESC, ic.id ASC
				LIMIT :limit
				""", new MapSqlParameterSource().addValue("since", utc(since)).addValue("limit", limit),
				(rs, rowNum) -> new PostInterestCategoryResponse(rs.getLong("id"), rs.getString("name")));
	}

	/**
	 * 글 카드와 상세에 쓰는 글 한 줄. 제목과 본문은 지금 보이는 버전이다.
	 *
	 * @param latestVersion 지금 보이는 버전의 번호. 2 이상이면 고친 글이다
	 */
	public record PostRow(Long id, Long authorId, PostCategory category, PostStatus status, String title,
			String content, int latestVersion, int viewCount, Instant createdAt) {
	}

	/** @return 글 id → 글. 받은 id의 순서를 따르고, 없는 id는 담지 않는다 */
	public Map<Long, PostRow> findRows(List<Long> postIds) {
		Map<Long, PostRow> result = new LinkedHashMap<>();
		if (postIds.isEmpty()) {
			return result;
		}
		Map<Long, PostRow> found = new HashMap<>();
		jdbc.query("SELECT p.id, p.author_id, p.category, p.status, p.view_count, p.created_at, v.title, v.content, "
				+ "v.version FROM post p " + JOIN_LATEST_VERSION + " WHERE p.id IN (:ids)",
				Map.of("ids", postIds), rs -> {
					PostRow row = new PostRow(rs.getLong("id"), rs.getLong("author_id"),
							PostCategory.valueOf(rs.getString("category")), PostStatus.valueOf(rs.getString("status")),
							rs.getString("title"), rs.getString("content"), rs.getInt("version"),
							rs.getInt("view_count"), instant(rs, "created_at"));
					found.put(row.id(), row);
				});
		postIds.stream().filter(found::containsKey).forEach(id -> result.put(id, found.get(id)));
		return result;
	}

	/** @return 글 id → 대표 사진(순서 1)의 주소. 사진이 없는 글은 담지 않는다 */
	public Map<Long, String> findThumbnailUrls(Collection<Long> postIds) {
		Map<Long, String> result = new HashMap<>();
		if (postIds.isEmpty()) {
			return result;
		}
		jdbc.query("""
				SELECT pim.post_id, ui.image_url FROM post_image pim JOIN uploaded_image ui ON ui.id = pim.image_id
				WHERE pim.post_id IN (:ids) AND pim.sort_order = 1
				""", Map.of("ids", postIds), rs -> {
			result.put(rs.getLong("post_id"), rs.getString("image_url"));
		});
		return result;
	}

	/** 글의 사진. 보여 주는 순서대로. */
	public List<PostDetailResponse.Image> findImages(Long postId) {
		return jdbc.query("""
				SELECT ui.id, ui.image_url FROM post_image pim JOIN uploaded_image ui ON ui.id = pim.image_id
				WHERE pim.post_id = :postId ORDER BY pim.sort_order
				""", Map.of("postId", postId),
				(rs, rowNum) -> new PostDetailResponse.Image(rs.getLong("id"), rs.getString("image_url")));
	}

	/** @return 글 id → 붙인 대분류(id 순). 지운 대분류는 뺀다. 태그가 없는 글은 담지 않는다 */
	public Map<Long, List<PostInterestCategoryResponse>> findInterestCategories(Collection<Long> postIds) {
		Map<Long, List<PostInterestCategoryResponse>> result = new HashMap<>();
		if (postIds.isEmpty()) {
			return result;
		}
		jdbc.query("""
				SELECT pi.post_id, ic.id, ic.name FROM post_interest pi
				JOIN interest_category ic ON ic.id = pi.interest_category_id
				WHERE pi.post_id IN (:ids) AND ic.deleted_at IS NULL
				ORDER BY ic.id
				""", Map.of("ids", postIds), rs -> {
			result.computeIfAbsent(rs.getLong("post_id"), id -> new ArrayList<>())
					.add(new PostInterestCategoryResponse(rs.getLong("id"), rs.getString("name")));
		});
		return result;
	}

	/** 글에 붙인 대분류의 id. 지운 대분류도 담는다(다음 읽을 글을 고를 때 쓴다). */
	public List<Long> findInterestCategoryIds(Long postId) {
		return jdbc.queryForList("SELECT interest_category_id FROM post_interest WHERE post_id = :postId",
				Map.of("postId", postId), Long.class);
	}

	/** @return 글 id → 좋아요 수. 좋아요가 없는 글은 담지 않는다 */
	public Map<Long, Long> countLikes(Collection<Long> postIds) {
		return countByPost("SELECT post_id, COUNT(*) AS cnt FROM post_like WHERE post_id IN (:ids) GROUP BY post_id",
				postIds);
	}

	/** @return 글 id → 삭제되지 않은 댓글과 답글의 수. 댓글이 없는 글은 담지 않는다 */
	public Map<Long, Long> countComments(Collection<Long> postIds) {
		return countByPost("""
				SELECT post_id, COUNT(*) AS cnt FROM comment WHERE post_id IN (:ids) AND status <> 'DELETED'
				GROUP BY post_id
				""", postIds);
	}

	/** 이 글들 가운데 이 계정이 저장한 것. */
	public Set<Long> findBookmarked(Long userId, Collection<Long> postIds) {
		return findPostIdsOf("SELECT post_id FROM post_bookmark WHERE user_id = :userId AND post_id IN (:ids)",
				userId, postIds);
	}

	/** 이 글들 가운데 이 계정이 좋아요를 누른 것. */
	public Set<Long> findLiked(Long userId, Collection<Long> postIds) {
		return findPostIdsOf("SELECT post_id FROM post_like WHERE user_id = :userId AND post_id IN (:ids)",
				userId, postIds);
	}

	/**
	 * 작성자의 관심사 가운데 보는 사람과 겹치는 것. 작성자가 고른 순서대로. 지운 관심사는 뺀다.
	 */
	public List<PostDetailResponse.SharedInterest> findSharedInterests(Long authorId, Long viewerId) {
		return jdbc.query("""
				SELECT i.id, i.name, i.interest_category_id
				FROM user_interest author_ui
				JOIN interest i ON i.id = author_ui.interest_id
				JOIN user_interest viewer_ui ON viewer_ui.interest_id = author_ui.interest_id
				 AND viewer_ui.user_id = :viewerId
				WHERE author_ui.user_id = :authorId AND i.deleted_at IS NULL
				ORDER BY author_ui.sort_order
				""", Map.of("authorId", authorId, "viewerId", viewerId),
				(rs, rowNum) -> new PostDetailResponse.SharedInterest(rs.getLong("id"), rs.getString("name"),
						rs.getLong("interest_category_id"), true));
	}

	private Map<Long, Long> countByPost(String sql, Collection<Long> postIds) {
		Map<Long, Long> result = new HashMap<>();
		if (postIds.isEmpty()) {
			return result;
		}
		jdbc.query(sql, Map.of("ids", postIds), rs -> {
			result.put(rs.getLong("post_id"), rs.getLong("cnt"));
		});
		return result;
	}

	private Set<Long> findPostIdsOf(String sql, Long userId, Collection<Long> postIds) {
		if (postIds.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(jdbc.queryForList(sql, Map.of("userId", userId, "ids", postIds), Long.class));
	}

	/** {@code %}, {@code _}와 이스케이프 문자 자신을 글자 그대로 찾게 한다. */
	static String escapeLike(String keyword) {
		StringBuilder escaped = new StringBuilder(keyword.length());
		for (char c : keyword.toCharArray()) {
			if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
				escaped.append(LIKE_ESCAPE);
			}
			escaped.append(c);
		}
		return escaped.toString();
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		return rs.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
	}

}
