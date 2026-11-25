package com.itting.meetup.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.itting.chat.repository.ChatRoomReadRepository;
import com.itting.chat.repository.ChatRoomReadRepository.RoomReadState;
import com.itting.meetup.domain.MeetupCalendarScope;
import com.itting.meetup.domain.MeetupViewerStatus;
import com.itting.meetup.domain.MyMeetupTab;
import com.itting.meetup.domain.RecruitStatus;
import com.itting.meetup.dto.MeetupCalendarResponse;
import com.itting.meetup.dto.MyMeetupResponse;
import com.itting.meetup.dto.MyMeetupSummaryResponse;
import com.itting.meetup.repository.MeetupListRepository;
import com.itting.meetup.repository.MeetupListRepository.DashboardRow;
import com.itting.meetup.repository.MeetupListRepository.TabCounts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MeetupDashboardService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Instant FIRST_DB_DATETIME = Instant.parse("1000-01-01T00:00:00Z");

	private final MeetupListRepository meetups;
	private final ChatRoomReadRepository chat;
	private final Clock clock;

	public MeetupDashboardService(MeetupListRepository meetups, ChatRoomReadRepository chat, Clock clock) {
		this.meetups = meetups;
		this.chat = chat;
		this.clock = clock;
	}

	public MyMeetupResponse myMeetups(Long userId, MyMeetupTab tab) {
		Instant now = clock.instant();
		List<DashboardRow> rows = meetups.findMyMeetups(userId, tab, now);
		Map<Long, RoomReadState> rooms = chat.findForMeetups(userId, meetupIds(rows));
		return new MyMeetupResponse(rows.stream().map(row -> {
			RoomReadState room = rooms.get(row.card().id());
			boolean canEdit = row.myStatus() == MeetupViewerStatus.HOST
					&& row.card().recruitStatus() != RecruitStatus.CANCELED
					&& now.isBefore(row.card().startsAt().minus(Duration.ofHours(12)));
			return new MyMeetupResponse.ItemResponse(row.card(), row.myStatus(), row.liked(), row.vacancyAlert(),
					row.pendingCount(), canEdit, room == null ? null : room.roomId(), room == null ? 0 : room.unreadCount());
		}).toList());
	}

	public MyMeetupSummaryResponse summary(Long userId) {
		Instant now = clock.instant();
		TabCounts counts = meetups.countMyMeetups(userId, now);
		List<MyMeetupSummaryResponse.UpcomingItemResponse> items = meetups.findUpcomingSchedule(userId, now).stream()
				.map(row -> new MyMeetupSummaryResponse.UpcomingItemResponse(row.card(), row.myStatus())).toList();
		return new MyMeetupSummaryResponse(new MyMeetupSummaryResponse.CountsResponse(
				counts.upcoming(), counts.pending(), counts.hosting(), counts.liked(), counts.past()), items);
	}

	public MeetupCalendarResponse calendar(Long userId, YearMonth month, MeetupCalendarScope scope) {
		Instant from = month.atDay(1).atStartOfDay(SEOUL).toInstant();
		Instant until = month.plusMonths(1).atDay(1).atStartOfDay(SEOUL).toInstant();
		List<DashboardRow> rows = meetups.findCalendar(userId, clock.instant(),
				from.isBefore(FIRST_DB_DATETIME) ? FIRST_DB_DATETIME : from, until, scope);
		Map<Long, Long> roomIds = chat.findJoinedMeetupRoomIds(userId, meetupIds(rows));
		List<MeetupCalendarResponse.ItemResponse> items = rows.stream()
				.map(row -> new MeetupCalendarResponse.ItemResponse(row.card(), row.myStatus(), roomIds.get(row.card().id())))
				.toList();
		return new MeetupCalendarResponse(month.getYear(), month.getMonthValue(),
				new MeetupCalendarResponse.SummaryResponse(count(rows, MeetupViewerStatus.CONFIRMED),
						count(rows, MeetupViewerStatus.PENDING), count(rows, MeetupViewerStatus.HOST)), items);
	}

	private static long count(List<DashboardRow> rows, MeetupViewerStatus status) {
		return rows.stream().filter(row -> row.myStatus() == status).count();
	}

	private static List<Long> meetupIds(List<DashboardRow> rows) {
		return rows.stream().map(row -> row.card().id()).toList();
	}

}
