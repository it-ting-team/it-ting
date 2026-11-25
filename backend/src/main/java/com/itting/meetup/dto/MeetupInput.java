package com.itting.meetup.dto;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupDetails;
import com.itting.meetup.domain.MeetupMode;

public interface MeetupInput {

	String title();

	MeetupCategory category();

	String description();

	Integer capacity();

	String supplies();

	Boolean isMessageEnabled();

	Instant startsAt();

	Instant endsAt();

	MeetupMode mode();

	String place();

	String placeDetail();

	String joinUrl();

	default MeetupDetails details() {
		return new MeetupDetails(title(), category(), description(), capacity(), supplies(), isMessageEnabled(),
				startsAt().truncatedTo(ChronoUnit.MICROS),
				endsAt() == null ? null : endsAt().truncatedTo(ChronoUnit.MICROS),
				mode(), place(), placeDetail(), joinUrl());
	}

}
