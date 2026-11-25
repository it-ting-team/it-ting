package com.itting.meetup.service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.itting.meetup.domain.Meetup;
import com.itting.meetup.repository.MeetupRepository;
import com.itting.notification.domain.NotificationTargetType;
import com.itting.notification.service.NotificationTargetReader;
import com.itting.notification.service.NotificationTargetSummary;
import org.springframework.stereotype.Component;

@Component
public class MeetupNotificationTargetReader implements NotificationTargetReader {

	private final MeetupRepository meetups;

	public MeetupNotificationTargetReader(MeetupRepository meetups) {
		this.meetups = meetups;
	}

	@Override
	public NotificationTargetType targetType() {
		return NotificationTargetType.MEETUP;
	}

	@Override
	public Map<Long, NotificationTargetSummary> read(Collection<Long> targetIds) {
		return meetups.findAllByIdInAndDeletedAtIsNull(targetIds).stream()
				.collect(Collectors.toMap(Meetup::getId, meetup -> new NotificationTargetSummary(
						meetup.getDetails().title(), meetup.getDetails().startsAt())));
	}

}
