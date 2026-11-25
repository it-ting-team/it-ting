package com.itting.meetup.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.domain.MeetupSort;
import com.itting.meetup.domain.RecruitStatus;
import com.itting.meetup.dto.MeetupListResponse.MeetupCardResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MeetupListRepository {

	private static final String AVAILABLE = """
			FROM meetup m WHERE m.deleted_at IS NULL AND m.canceled_at IS NULL AND m.starts_at > :now
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public MeetupListRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Page search(Search search, Instant now, MeetupSort sort, long offset, int size) {
		StringBuilder source = new StringBuilder(AVAILABLE);
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("now", utc(now));
		if (search.keyword() != null && !search.keyword().isEmpty()) {
			String escaped = search.keyword().replace("!", "!!").replace("%", "!%").replace("_", "!_");
			params.addValue("keyword", "%" + escaped + "%");
			source.append(" AND (m.title LIKE :keyword ESCAPE '!' OR m.description LIKE :keyword ESCAPE '!')");
		}
		if (!search.categories().isEmpty()) {
			params.addValue("categories", search.categories().stream().map(MeetupCategory::name).toList());
			source.append(" AND m.category IN (:categories)");
		}
		if (search.from() != null) {
			params.addValue("from", utc(search.from()));
			source.append(" AND m.starts_at >= :from");
		}
		if (search.until() != null) {
			params.addValue("until", utc(search.until()));
			source.append(" AND m.starts_at < :until");
		}
		if (search.mode() != null) {
			params.addValue("mode", search.mode().name());
			source.append(" AND m.mode = :mode");
		}

		long total = jdbc.queryForObject("SELECT COUNT(*) " + source, params, Long.class);
		if (offset >= total) {
			return new Page(List.of(), total);
		}
		params.addValue("offset", offset).addValue("size", size);
		String order = switch (sort) {
			case STARTS_SOON -> "starts_at ASC";
			case NEWEST -> "created_at DESC";
			case POPULAR -> "like_count DESC";
			case MOST_JOINED -> "confirmed_count DESC";
		};
		String sql = """
				SELECT matched.*,
				       (confirmed_count >= capacity OR deadline <= :now) AS is_closed
				FROM (
				    SELECT m.id, m.title, m.category, m.starts_at, m.ends_at, m.mode, m.place, m.capacity, m.created_at,
				           COALESCE(m.recruit_deadline_at, m.starts_at) AS deadline,
				           1 + (SELECT COUNT(*) FROM meetup_application a
				                WHERE a.meetup_id = m.id AND a.status = 'CONFIRMED') AS confirmed_count,
				           (SELECT COUNT(*) FROM meetup_like l WHERE l.meetup_id = m.id) AS like_count
				%s
				) matched
				ORDER BY is_closed ASC, %s, starts_at ASC, id DESC
				LIMIT :size OFFSET :offset
				""".formatted(source, order);
		return new Page(jdbc.query(sql, params, (rs, row) -> toCard(rs)), total);
	}

	private static MeetupCardResponse toCard(ResultSet rs) throws SQLException {
		LocalDateTime end = rs.getObject("ends_at", LocalDateTime.class);
		return new MeetupCardResponse(rs.getLong("id"), rs.getString("title"),
				MeetupCategory.valueOf(rs.getString("category")),
				rs.getObject("starts_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
				end == null ? null : end.toInstant(ZoneOffset.UTC), MeetupMode.valueOf(rs.getString("mode")),
				rs.getString("place"), rs.getInt("capacity"), rs.getLong("confirmed_count"),
				rs.getBoolean("is_closed") ? RecruitStatus.CLOSED : RecruitStatus.OPEN);
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	public record Search(String keyword, List<MeetupCategory> categories, Instant from, Instant until, MeetupMode mode) {
	}

	public record Page(List<MeetupCardResponse> items, long totalItems) {
	}

}
