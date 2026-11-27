package com.itting.meetup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.itting.meetup.domain.Meetup;
import com.itting.meetup.domain.MeetupCategory;
import com.itting.meetup.domain.MeetupDetails;
import com.itting.meetup.domain.MeetupMode;
import com.itting.meetup.dto.MeetupListResponse.MeetupCardResponse;
import com.itting.meetup.repository.MeetupListRepository;
import com.itting.meetup.repository.MeetupParticipationRepository;
import com.itting.meetup.repository.MeetupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅이 모임의 값을 읽는 창구. 모임 대화방의 모임 정보, 모임이 끝났는지와 참여를 취소할 수 있는지, 메시지 안의 모임 카드를
 * 모임 영역의 계산(확정 인원, 시각의 경계)으로 답한다. 채팅은 이 값을 따로 계산하지 않는다. 데이터를 바꾸지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class MeetupChatInfoReader {

	private final MeetupRepository meetups;

	private final MeetupParticipationRepository participations;

	private final MeetupListRepository lists;

	private final Clock clock;

	public MeetupChatInfoReader(MeetupRepository meetups, MeetupParticipationRepository participations,
			MeetupListRepository lists, Clock clock) {
		this.meetups = meetups;
		this.participations = participations;
		this.lists = lists;
		this.clock = clock;
	}

	/** 삭제되지 않은 모임인지. 삭제된 모임의 대화방에는 들어갈 수 없다. */
	public boolean exists(Long meetupId) {
		return meetups.findByIdAndDeletedAtIsNull(meetupId).isPresent();
	}

	/**
	 * 모임 대화방에 보여 줄 모임의 지금 값. 취소된 모임도 준다.
	 *
	 * @return 없거나 삭제된 모임이면 비어 있다
	 */
	public Optional<MeetupChatInfo> find(Long meetupId) {
		Instant now = clock.instant();
		return meetups.findByIdAndDeletedAtIsNull(meetupId).map(meetup -> toInfo(meetup, now));
	}

	/**
	 * 메시지 안의 모임 카드에 쓸 값을 한 번에 읽는다. 취소된 모임도 담는다.
	 *
	 * @return 모임 id → 카드. 없거나 삭제된 모임은 담지 않는다
	 */
	public Map<Long, MeetupCardResponse> findCards(Collection<Long> meetupIds) {
		return lists.findCards(meetupIds, clock.instant());
	}

	private MeetupChatInfo toInfo(Meetup meetup, Instant now) {
		MeetupDetails d = meetup.getDetails();
		long confirmedCount = 1 + participations.countConfirmed(meetup.getId());
		return new MeetupChatInfo(meetup.getId(), meetup.getHostId(), d.title(), d.category(), d.startsAt(), d.endsAt(),
				d.mode(), d.place(), d.placeDetail(), d.joinUrl(), d.capacity(), confirmedCount, meetup.isCanceled(),
				meetup.isEnded(now), meetup.canCancelParticipation(now));
	}

	/**
	 * 모임 대화방이 쓰는 모임의 값.
	 *
	 * @param confirmedCount 확정 인원. 주최자 1명에 {@code CONFIRMED} 신청 수를 더한 값
	 * @param isCanceled 취소된 모임인지
	 * @param isEnded 끝난 모임인지. 끝나는 시각이 없으면 시작 시각으로 본다
	 * @param canCancelParticipation 참여자가 지금 참여를 취소할 수 있는지(취소되지 않았고 시작 24시간 전이 지나지 않음)
	 */
	public record MeetupChatInfo(Long id, Long hostId, String title, MeetupCategory category, Instant startsAt,
			Instant endsAt, MeetupMode mode, String place, String placeDetail, String joinUrl, int capacity,
			long confirmedCount, boolean isCanceled, boolean isEnded, boolean canCancelParticipation) {

		/** 대화방에서만 나갈 수 있는 때인지. 취소됐거나 끝난 모임이다. */
		public boolean isOver() {
			return isCanceled || isEnded;
		}

	}

}
