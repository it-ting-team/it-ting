package com.itting.meetup.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.itting.meetup.domain.MeetupApplicationStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MeetupParticipationRepository {

	private static final String SELECT_APPLICATION = "SELECT id, meetup_id, user_id, status, message FROM meetup_application ";
	private static final RowMapper<Application> APPLICATION = (row, index) -> new Application(
			row.getLong("id"), row.getLong("meetup_id"), row.getLong("user_id"),
			MeetupApplicationStatus.valueOf(row.getString("status")), row.getString("message"));

	private final NamedParameterJdbcTemplate jdbc;

	public MeetupParticipationRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Long> findConfirmedUsers(Long meetupId) {
		return jdbc.queryForList("""
				SELECT user_id FROM meetup_application WHERE meetup_id = :id AND status = 'CONFIRMED'
				ORDER BY status_changed_at, id
				""", Map.of("id", meetupId), Long.class);
	}

	public List<Long> findConfirmedBy(Long meetupId, Instant due) {
		return jdbc.queryForList("""
				SELECT user_id FROM meetup_application WHERE meetup_id = :id AND status = 'CONFIRMED'
				  AND status_changed_at <= :due
				""", Map.of("id", meetupId, "due", LocalDateTime.ofInstant(due, ZoneOffset.UTC)), Long.class);
	}

	public List<Long> findPendingUsers(Long meetupId) {
		return jdbc.queryForList("""
				SELECT user_id FROM meetup_application WHERE meetup_id = :id AND status = 'PENDING'
				""", Map.of("id", meetupId), Long.class);
	}

	public Optional<MeetupApplicationStatus> findStatus(Long meetupId, Long userId) {
		return jdbc.queryForList("""
				SELECT status FROM meetup_application WHERE meetup_id = :id AND user_id = :userId
				""", Map.of("id", meetupId, "userId", userId), String.class)
				.stream().findFirst().map(MeetupApplicationStatus::valueOf);
	}

	public Optional<Application> findApplication(Long applicationId) {
		return jdbc.query(SELECT_APPLICATION + "WHERE id = :id", Map.of("id", applicationId), APPLICATION)
				.stream().findFirst();
	}

	public Optional<Application> findApplication(Long meetupId, Long userId) {
		return jdbc.query(SELECT_APPLICATION + "WHERE meetup_id = :meetup AND user_id = :user",
				Map.of("meetup", meetupId, "user", userId), APPLICATION).stream().findFirst();
	}

	public List<Application> findForManagement(Long meetupId, boolean includePending) {
		return jdbc.query(SELECT_APPLICATION + """
				WHERE meetup_id = :id AND (status = 'CONFIRMED' OR (:includePending AND status = 'PENDING'))
				ORDER BY (status = 'PENDING') DESC, status_changed_at, id
				""", Map.of("id", meetupId, "includePending", includePending), APPLICATION);
	}

	public long countConfirmed(Long meetupId) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM meetup_application WHERE meetup_id = :id AND status = 'CONFIRMED'
				""", Map.of("id", meetupId), Long.class);
	}

	public long countPendingForHost(Long hostId, Instant now) {
		return jdbc.queryForObject("""
				SELECT COUNT(*) FROM meetup_application a JOIN meetup m ON m.id = a.meetup_id
				WHERE m.host_id = :hostId AND m.deleted_at IS NULL AND m.canceled_at IS NULL
				  AND COALESCE(m.recruit_deadline_at, m.starts_at) > :now AND a.status = 'PENDING'
				""", Map.of("hostId", hostId, "now", utc(now)), Long.class);
	}

	public List<Long> findExpiredWithPending(Instant now, long afterId, int limit) {
		return jdbc.queryForList("""
				SELECT m.id FROM meetup m
				WHERE m.id > :afterId AND m.deleted_at IS NULL AND m.canceled_at IS NULL
				  AND COALESCE(m.recruit_deadline_at, m.starts_at) <= :now
				  AND EXISTS (SELECT 1 FROM meetup_application a WHERE a.meetup_id = m.id AND a.status = 'PENDING')
				ORDER BY m.id LIMIT :limit
				""", Map.of("now", utc(now), "afterId", afterId, "limit", limit), Long.class);
	}

	public Long insertApplication(Long meetupId, Long userId, String message, Instant now) {
		GeneratedKeyHolder key = new GeneratedKeyHolder();
		MapSqlParameterSource parameters = new MapSqlParameterSource()
				.addValue("meetupId", meetupId).addValue("userId", userId)
				.addValue("message", message).addValue("now", utc(now));
		jdbc.update("""
				INSERT INTO meetup_application (meetup_id, user_id, status, message, created_at, status_changed_at)
				VALUES (:meetupId, :userId, 'PENDING', :message, :now, :now)
				""", parameters, key, new String[] {"id"});
		return key.getKey().longValue();
	}

	public void reopenApplication(Long applicationId, String message, Instant now) {
		jdbc.update("""
				UPDATE meetup_application SET status = 'PENDING', message = :message, status_changed_at = :now
				WHERE id = :id
				""", new MapSqlParameterSource().addValue("id", applicationId)
				.addValue("message", message).addValue("now", utc(now)));
	}

	public void changeStatus(Long applicationId, MeetupApplicationStatus status, Instant now) {
		jdbc.update("""
				UPDATE meetup_application SET status = :status, status_changed_at = :now WHERE id = :id
				""", Map.of("id", applicationId, "status", status.name(), "now", utc(now)));
	}

	public Optional<Boolean> findVacancySubscription(Long meetupId, Long userId) {
		return jdbc.queryForList("""
				SELECT vacancy_alert FROM meetup_like WHERE meetup_id = :id AND user_id = :userId
				""", Map.of("id", meetupId, "userId", userId), Boolean.class).stream().findFirst();
	}

	public List<Long> findVacancySubscribers(Long meetupId) {
		return jdbc.queryForList("""
				SELECT user_id FROM meetup_like WHERE meetup_id = :id AND vacancy_alert = TRUE
				""", Map.of("id", meetupId), Long.class);
	}

	public void closePendingOnCancellation(Long meetupId, Instant now) {
		closePending(meetupId, MeetupApplicationStatus.MEETUP_CANCELED, now);
	}

	public void rejectExpiredApplications(Long meetupId, Instant now) {
		closePending(meetupId, MeetupApplicationStatus.REJECTED, now);
	}

	private void closePending(Long meetupId, MeetupApplicationStatus status, Instant now) {
		jdbc.update("""
				UPDATE meetup_application SET status = :status, status_changed_at = :now
				WHERE meetup_id = :id AND status = 'PENDING'
				""", Map.of("id", meetupId, "status", status.name(), "now", utc(now)));
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	public record Application(Long id, Long meetupId, Long userId, MeetupApplicationStatus status, String message) {
	}

}
