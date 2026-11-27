package com.itting.community.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 정리 작업({@link com.itting.community.service.CommunityCleanupJob})이 쓰는 쿼리. 한 번에 {@code limit}줄까지만
 * 지워 오래 잠그지 않는다. 시각은 UTC의 날짜 · 시각으로 준다(DB에 UTC로 저장되어 있다).
 */
@Repository
public class CommunityCleanupRepository {

	/** 글에도 채팅 메시지에도 붙지 않은 사진. {@code u}는 {@code uploaded_image}다. */
	private static final String UNATTACHED = """
			NOT EXISTS (SELECT 1 FROM post_image p WHERE p.image_id = u.id)
			AND NOT EXISTS (SELECT 1 FROM chat_message_image c WHERE c.image_id = u.id)
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public CommunityCleanupRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 지울 사진 한 장. */
	public record ImageRow(long id, String imageUrl) {
	}

	/**
	 * {@code before}보다 먼저 삭제한 글의 버전을 {@code limit}줄까지 지운다.
	 *
	 * @return 지운 줄 수
	 */
	public int deletePostVersions(Instant before, int limit) {
		return jdbc.update("""
				DELETE FROM post_version
				WHERE post_id IN (SELECT id FROM post WHERE status = 'DELETED' AND deleted_at < :before)
				LIMIT :limit
				""", params(before, limit));
	}

	/**
	 * {@code before}보다 먼저 삭제한 글에서 사진을 {@code limit}장까지 뗀다. 뗀 사진({@code uploaded_image})은
	 * 붙지 않은 사진이 되어 {@link #findUnattachedImages}가 고른다.
	 *
	 * @return 뗀 줄 수
	 */
	public int deletePostImages(Instant before, int limit) {
		return jdbc.update("""
				DELETE FROM post_image
				WHERE post_id IN (SELECT id FROM post WHERE status = 'DELETED' AND deleted_at < :before)
				LIMIT :limit
				""", params(before, limit));
	}

	/**
	 * {@code before}보다 먼저 삭제한 댓글 · 답글의 버전을 {@code limit}줄까지 지운다.
	 *
	 * @return 지운 줄 수
	 */
	public int deleteCommentVersions(Instant before, int limit) {
		return jdbc.update("""
				DELETE FROM comment_version
				WHERE comment_id IN (SELECT id FROM comment WHERE status = 'DELETED' AND deleted_at < :before)
				LIMIT :limit
				""", params(before, limit));
	}

	/** {@code before}보다 먼저 올렸고 어디에도 붙지 않은 사진을 id 순서로 {@code limit}장까지 고른다. */
	public List<ImageRow> findUnattachedImages(Instant before, int limit) {
		return jdbc.query("SELECT u.id, u.image_url FROM uploaded_image u WHERE u.created_at < :before AND "
				+ UNATTACHED + " ORDER BY u.id LIMIT :limit", params(before, limit),
				(rs, rowNum) -> new ImageRow(rs.getLong("id"), rs.getString("image_url")));
	}

	/**
	 * 고른 사진 가운데 지금도 어디에도 붙지 않은 것만 지운다. 고른 뒤에 글이나 메시지에 붙은 사진은 남는다.
	 *
	 * @return 지운 줄 수
	 */
	public int deleteUnattachedImages(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return 0;
		}
		return jdbc.update("DELETE FROM uploaded_image u WHERE u.id IN (:ids) AND " + UNATTACHED,
				Map.of("ids", ids));
	}

	/** 이 사진들 가운데 줄이 남아 있는 것의 id. */
	public List<Long> findExistingImageIds(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.queryForList("SELECT id FROM uploaded_image WHERE id IN (:ids)", Map.of("ids", ids),
				Long.class);
	}

	private static MapSqlParameterSource params(Instant before, int limit) {
		return new MapSqlParameterSource().addValue("before", LocalDateTime.ofInstant(before, ZoneOffset.UTC))
				.addValue("limit", limit);
	}

}
