package com.itting.meetup.service;

import java.time.Instant;
import java.util.Optional;

/** 모임 API가 모임 대화방 구현에 요청하는 생성·접근 확인. */
public interface MeetupChatRooms {

	Long create(Long meetupId, Long hostId, Instant joinedAt);

	Optional<Long> findJoinedRoom(Long meetupId, Long userId);

	void join(Long meetupId, Long userId, Instant now);

	void leave(Long meetupId, Long userId, Instant now);

}
