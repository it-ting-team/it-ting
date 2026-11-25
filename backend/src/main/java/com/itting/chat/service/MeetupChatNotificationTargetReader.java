package com.itting.chat.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.itting.chat.domain.ChatRoom;
import com.itting.chat.repository.ChatRoomRepository;
import com.itting.meetup.service.MeetupNotificationTargetReader;
import com.itting.notification.domain.NotificationTargetType;
import com.itting.notification.service.NotificationTargetReader;
import com.itting.notification.service.NotificationTargetSummary;
import org.springframework.stereotype.Component;

@Component
public class MeetupChatNotificationTargetReader implements NotificationTargetReader {

	private final ChatRoomRepository rooms;
	private final MeetupNotificationTargetReader meetups;

	public MeetupChatNotificationTargetReader(ChatRoomRepository rooms, MeetupNotificationTargetReader meetups) {
		this.rooms = rooms;
		this.meetups = meetups;
	}

	@Override
	public NotificationTargetType targetType() {
		return NotificationTargetType.CHAT_ROOM;
	}

	@Override
	public Map<Long, NotificationTargetSummary> read(Collection<Long> targetIds) {
		List<ChatRoom> meetupRooms = rooms.findAllById(targetIds).stream()
				.filter(room -> room.getMeetupId() != null)
				.toList();
		if (meetupRooms.isEmpty()) {
			return Map.of();
		}
		Map<Long, NotificationTargetSummary> summaries = meetups.read(meetupRooms.stream()
				.map(ChatRoom::getMeetupId).toList());
		Map<Long, NotificationTargetSummary> targets = new HashMap<>();
		for (ChatRoom room : meetupRooms) {
			NotificationTargetSummary summary = summaries.get(room.getMeetupId());
			if (summary != null) {
				targets.put(room.getId(), new NotificationTargetSummary(summary.title(), null));
			}
		}
		return targets;
	}

}
