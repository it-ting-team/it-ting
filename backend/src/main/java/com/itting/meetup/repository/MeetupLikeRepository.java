package com.itting.meetup.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MeetupLikeRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public MeetupLikeRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insertIfAbsent(Long meetupId, Long userId, Instant now) {
		jdbc.update("""
				INSERT INTO meetup_like (meetup_id, user_id, vacancy_alert, created_at, updated_at)
				VALUES (:meetupId, :userId, FALSE, :now, :now)
				ON DUPLICATE KEY UPDATE meetup_id = meetup_like.meetup_id
				""", Map.of("meetupId", meetupId, "userId", userId, "now", LocalDateTime.ofInstant(now, ZoneOffset.UTC)));
	}

	/** 이 사람의 찜과 빈자리 알림을 모두 지운다(회원 탈퇴). */
	public void deleteAllByUser(Long userId) {
		jdbc.update("DELETE FROM meetup_like WHERE user_id = :userId", Map.of("userId", userId));
	}

	public void delete(Long meetupId, Long userId) {
		jdbc.update("DELETE FROM meetup_like WHERE meetup_id = :meetupId AND user_id = :userId",
				Map.of("meetupId", meetupId, "userId", userId));
	}

	public Optional<Boolean> findAlertForUpdate(Long meetupId, Long userId) {
		return jdbc.queryForList("""
				SELECT vacancy_alert FROM meetup_like WHERE meetup_id = :meetupId AND user_id = :userId FOR UPDATE
				""", Map.of("meetupId", meetupId, "userId", userId), Boolean.class).stream().findFirst();
	}

	public void setAlert(Long meetupId, Long userId, boolean enabled, Instant now) {
		jdbc.update("""
				UPDATE meetup_like SET vacancy_alert = :enabled, updated_at = :now
				WHERE meetup_id = :meetupId AND user_id = :userId AND vacancy_alert <> :enabled
				""", Map.of("meetupId", meetupId, "userId", userId, "enabled", enabled,
				"now", LocalDateTime.ofInstant(now, ZoneOffset.UTC)));
	}

}
