package com.itting.meetup.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.itting.meetup.domain.MeetupApplicationStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MeetupParticipationRepository {

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
		jdbc.update("""
				UPDATE meetup_application SET status = 'MEETUP_CANCELED', status_changed_at = :now
				WHERE meetup_id = :id AND status = 'PENDING'
				""", Map.of("id", meetupId, "now", LocalDateTime.ofInstant(now, ZoneOffset.UTC)));
	}

}
