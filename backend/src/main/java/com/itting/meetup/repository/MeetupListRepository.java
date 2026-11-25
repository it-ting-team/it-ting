package com.itting.meetup.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupCalendarScope;
import com.itting.meetup.domain.MeetupLinkScope;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.domain.MeetupSort;
import com.itting.meetup.domain.MeetupViewerStatus;
import com.itting.meetup.domain.MyMeetupTab;
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

	/**
	 * 다른 영역에 연결할 모임을 고를 때의 후보 줄. 취소된 모임까지 담고, 삭제된 모임은 뺀다.
	 * 확정 인원은 {@link #search}와 같이 주최자 1명에 {@code CONFIRMED} 신청 수를 더한다.
	 */
	private static final String CARD_COLUMNS = """
			m.id, m.title, m.category, m.starts_at, m.ends_at, m.mode, m.place, m.capacity, m.canceled_at,
			       COALESCE(m.recruit_deadline_at, m.starts_at) AS deadline,
			       1 + (SELECT COUNT(*) FROM meetup_application a
			            WHERE a.meetup_id = m.id AND a.status = 'CONFIRMED') AS confirmed_count
			""";

	private static final String CARD_SOURCE = "SELECT " + CARD_COLUMNS + " FROM meetup m WHERE m.deleted_at IS NULL";

	/** 모집 상태 계산. {@code Meetup.recruitStatus}와 같은 순서 · 같은 경계(시작 · 마감 시각은 "같거나 뒤")다. */
	private static final String RECRUIT_STATUS = """
			CASE WHEN canceled_at IS NOT NULL THEN 'CANCELED'
			     WHEN starts_at <= :now THEN 'PAST'
			     WHEN confirmed_count >= capacity OR deadline <= :now THEN 'CLOSED'
			     ELSE 'OPEN' END""";

	/** 내가 열었거나 참여가 확정된 모임. */
	private static final String HOSTED_OR_CONFIRMED = """
			(m.host_id = :userId OR EXISTS (SELECT 1 FROM meetup_application p
			                                WHERE p.meetup_id = m.id AND p.user_id = :userId AND p.status = 'CONFIRMED'))""";

	private static final String DASHBOARD_SOURCE = """
			WITH viewer_meetups AS (
				SELECT %s,
				       m.host_id = :userId AS is_host,
				       l.user_id IS NOT NULL AS is_liked, COALESCE(l.vacancy_alert, FALSE) AS vacancy_alert,
				       CASE WHEN m.host_id = :userId THEN 'HOST'
				            WHEN a.status IS NULL OR a.status = 'WITHDRAWN' THEN 'NONE'
				            WHEN a.status = 'PENDING' AND COALESCE(m.recruit_deadline_at, m.starts_at) <= :now
				                 THEN 'REJECTED'
				            ELSE a.status END AS my_status
				FROM meetup m
				LEFT JOIN meetup_application a ON a.meetup_id = m.id AND a.user_id = :userId
				LEFT JOIN meetup_like l ON l.meetup_id = m.id AND l.user_id = :userId
				WHERE m.deleted_at IS NULL AND (%s)
			)
			""";

	private static final String UPCOMING = "starts_at > :now AND canceled_at IS NULL";
	private static final String MINE = "my_status IN ('HOST', 'CONFIRMED', 'PENDING')";

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

	/**
	 * 이 사람이 이 기준으로 연결할 수 있는 모임. 취소 · 삭제된 모임은 없다.
	 *
	 * @param meetupId 이 모임만 보고 싶을 때. 아니면 null
	 */
	public List<MeetupCardResponse> findLinkable(Long userId, MeetupLinkScope scope, Instant now, Long meetupId) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("now", utc(now))
				.addValue("userId", userId);
		StringBuilder source = new StringBuilder(CARD_SOURCE).append(" AND m.canceled_at IS NULL");
		switch (scope) {
			case HOSTED_OPEN -> source.append(" AND m.host_id = :userId AND m.starts_at > :now");
			case JOINED_PAST -> source.append(" AND m.starts_at <= :now AND ").append(HOSTED_OR_CONFIRMED);
			case JOINED -> source.append(" AND ").append(HOSTED_OR_CONFIRMED);
		}
		if (meetupId != null) {
			params.addValue("meetupId", meetupId);
			source.append(" AND m.id = :meetupId");
		}
		boolean open = scope == MeetupLinkScope.HOSTED_OPEN;
		String order = open ? "starts_at ASC, id ASC" : "starts_at DESC, id DESC";
		String sql = "SELECT matched.*, " + RECRUIT_STATUS + " AS recruit_status FROM (" + source + ") matched"
				+ (open ? " WHERE " + RECRUIT_STATUS + " = 'OPEN'" : "") + " ORDER BY " + order;
		return jdbc.query(sql, params, (rs, row) -> toCard(rs, RecruitStatus.valueOf(rs.getString("recruit_status"))));
	}

	/** 삭제되지 않은 모임 하나의 카드. 취소된 모임은 {@code CANCELED}로, 지난 모임은 {@code PAST}로 담긴다. */
	public Optional<MeetupCardResponse> findCard(Long meetupId, Instant now) {
		String sql = "SELECT matched.*, " + RECRUIT_STATUS + " AS recruit_status FROM (" + CARD_SOURCE
				+ " AND m.id = :meetupId) matched";
		return jdbc.query(sql, new MapSqlParameterSource().addValue("now", utc(now)).addValue("meetupId", meetupId),
				(rs, row) -> toCard(rs, RecruitStatus.valueOf(rs.getString("recruit_status")))).stream().findFirst();
	}

	public List<DashboardRow> findMyMeetups(Long userId, MyMeetupTab tab, Instant now) {
		String order = switch (tab) {
			case LIKED -> "(recruit_status = 'CLOSED'), starts_at ASC, id DESC";
			case PAST -> "starts_at DESC, id DESC";
			default -> "starts_at ASC, id DESC";
		};
		return dashboardRows(dashboardSource("m.host_id = :userId OR a.id IS NOT NULL OR l.user_id IS NOT NULL")
				+ dashboardProjection() + " WHERE " + tabFilter(tab) + " ORDER BY " + order, viewer(userId, now));
	}

	public TabCounts countMyMeetups(Long userId, Instant now) {
		String sql = dashboardSource("m.host_id = :userId OR a.id IS NOT NULL OR l.user_id IS NOT NULL") + """
				SELECT COUNT(CASE WHEN %s THEN 1 END) AS upcoming,
				       COUNT(CASE WHEN %s THEN 1 END) AS pending,
				       COUNT(CASE WHEN %s THEN 1 END) AS hosting,
				       COUNT(CASE WHEN %s THEN 1 END) AS liked,
				       COUNT(CASE WHEN %s THEN 1 END) AS past
				FROM viewer_meetups
				""".formatted(tabFilter(MyMeetupTab.UPCOMING), tabFilter(MyMeetupTab.PENDING), tabFilter(MyMeetupTab.HOSTING),
				tabFilter(MyMeetupTab.LIKED), tabFilter(MyMeetupTab.PAST));
		return jdbc.queryForObject(sql, viewer(userId, now), (rs, row) -> new TabCounts(rs.getLong("upcoming"),
				rs.getLong("pending"), rs.getLong("hosting"), rs.getLong("liked"), rs.getLong("past")));
	}

	public List<DashboardRow> findUpcomingSchedule(Long userId, Instant now) {
		return dashboardRows(dashboardSource("m.host_id = :userId OR a.id IS NOT NULL")
				+ dashboardProjection() + " WHERE " + UPCOMING + " AND " + MINE + " ORDER BY starts_at, id DESC LIMIT 5",
				viewer(userId, now));
	}

	public List<DashboardRow> findCalendar(Long userId, Instant now, Instant from, Instant until, MeetupCalendarScope scope) {
		String range = "m.canceled_at IS NULL AND m.starts_at >= :from AND m.starts_at < :until";
		String scopeFilter = scope == MeetupCalendarScope.MINE ? " WHERE " + MINE : "";
		return dashboardRows(dashboardSource(range) + dashboardProjection() + scopeFilter + " ORDER BY starts_at, id DESC",
				viewer(userId, now).addValue("from", utc(from)).addValue("until", utc(until)));
	}

	private List<DashboardRow> dashboardRows(String sql, MapSqlParameterSource params) {
		return jdbc.query(sql, params, (rs, row) -> new DashboardRow(
				toCard(rs, RecruitStatus.valueOf(rs.getString("recruit_status"))),
				MeetupViewerStatus.valueOf(rs.getString("my_status")), rs.getBoolean("is_liked"),
				rs.getBoolean("vacancy_alert"), rs.getLong("pending_count")));
	}

	private static String dashboardSource(String filter) {
		return DASHBOARD_SOURCE.formatted(CARD_COLUMNS, filter);
	}

	private static String dashboardProjection() {
		return "SELECT v.*, " + RECRUIT_STATUS + """
				 AS recruit_status,
				 CASE WHEN is_host AND canceled_at IS NULL AND deadline > :now
				      THEN (SELECT COUNT(*) FROM meetup_application pending
				            WHERE pending.meetup_id = v.id AND pending.status = 'PENDING')
				      ELSE 0 END AS pending_count
				FROM viewer_meetups v
				""";
	}

	private static MapSqlParameterSource viewer(Long userId, Instant now) {
		return new MapSqlParameterSource().addValue("userId", userId).addValue("now", utc(now));
	}

	private static String tabFilter(MyMeetupTab tab) {
		return switch (tab) {
			case UPCOMING -> UPCOMING + " AND my_status IN ('HOST', 'CONFIRMED')";
			case PENDING -> UPCOMING + " AND my_status IN ('PENDING', 'REJECTED')";
			case HOSTING -> UPCOMING + " AND is_host";
			case LIKED -> UPCOMING + " AND is_liked";
			case PAST -> "(starts_at <= :now OR canceled_at IS NOT NULL) AND my_status IN ('HOST', 'CONFIRMED')";
		};
	}

	private static MeetupCardResponse toCard(ResultSet rs) throws SQLException {
		return toCard(rs, rs.getBoolean("is_closed") ? RecruitStatus.CLOSED : RecruitStatus.OPEN);
	}

	private static MeetupCardResponse toCard(ResultSet rs, RecruitStatus recruitStatus) throws SQLException {
		LocalDateTime end = rs.getObject("ends_at", LocalDateTime.class);
		return new MeetupCardResponse(rs.getLong("id"), rs.getString("title"),
				MeetupCategory.valueOf(rs.getString("category")),
				rs.getObject("starts_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
				end == null ? null : end.toInstant(ZoneOffset.UTC), MeetupMode.valueOf(rs.getString("mode")),
				rs.getString("place"), rs.getInt("capacity"), rs.getLong("confirmed_count"), recruitStatus);
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	public record Search(String keyword, List<MeetupCategory> categories, Instant from, Instant until, MeetupMode mode) {
	}

	public record Page(List<MeetupCardResponse> items, long totalItems) {
	}

	public record DashboardRow(MeetupCardResponse card, MeetupViewerStatus myStatus, boolean liked,
			boolean vacancyAlert, long pendingCount) {
	}

	public record TabCounts(long upcoming, long pending, long hosting, long liked, long past) {
	}

}
