package com.itting.meetup.domain;

import java.time.Duration;
import java.time.Instant;

import com.itting.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meetup")
public class Meetup extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "host_id", nullable = false, updatable = false)
	private Long hostId;

	@Embedded
	private MeetupDetails details;

	@Column(name = "recruit_deadline_at", updatable = false)
	private Instant recruitDeadlineAt;

	@Column(name = "canceled_at")
	private Instant canceledAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Meetup() {
	}

	public Meetup(Long hostId, MeetupDetails details, Instant recruitDeadlineAt) {
		this.hostId = hostId;
		this.details = details;
		this.recruitDeadlineAt = recruitDeadlineAt;
	}

	public Long getId() {
		return id;
	}

	public Long getHostId() {
		return hostId;
	}

	public MeetupDetails getDetails() {
		return details;
	}

	public Instant getRecruitDeadlineAt() {
		return recruitDeadlineAt;
	}

	public boolean isCanceled() {
		return canceledAt != null;
	}

	public boolean isStarted(Instant now) {
		return !now.isBefore(details.startsAt());
	}

	/**
	 * 모임이 끝났는지. 끝나는 시각을 정하지 않은 모임은 시작 시각으로 본다. 그 시각이 되면 끝난 것이다
	 * ({@code docs/api/06-chat.md}의 "대화방에서 나가기").
	 */
	public boolean isEnded(Instant now) {
		return !now.isBefore(details.endsAt() == null ? details.startsAt() : details.endsAt());
	}

	public boolean isRecruitmentEnded(Instant now) {
		return !now.isBefore(recruitDeadlineAt == null ? details.startsAt() : recruitDeadlineAt);
	}

	public boolean canEdit(Instant now) {
		return !isCanceled() && now.isBefore(details.startsAt().minus(Duration.ofHours(12)));
	}

	public boolean canCancelParticipation(Instant now) {
		return !isCanceled() && now.isBefore(details.startsAt().minus(Duration.ofHours(24)));
	}

	public RecruitStatus recruitStatus(Instant now, long confirmedCount) {
		if (isCanceled()) {
			return RecruitStatus.CANCELED;
		}
		if (isStarted(now)) {
			return RecruitStatus.PAST;
		}
		return isRecruitmentEnded(now) || confirmedCount >= details.capacity() ? RecruitStatus.CLOSED : RecruitStatus.OPEN;
	}

	public void update(MeetupDetails newDetails) {
		details = newDetails;
	}

	public void cancel(Instant now) {
		canceledAt = now;
	}

	public void delete(Instant now) {
		deletedAt = now;
	}

}
