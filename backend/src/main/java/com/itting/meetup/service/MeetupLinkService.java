package com.itting.meetup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.itting.global.error.BusinessException;
import com.itting.meetup.domain.MeetupErrorCode;
import com.itting.meetup.domain.MeetupLinkScope;
import com.itting.meetup.dto.MeetupListResponse.MeetupCardResponse;
import com.itting.meetup.repository.MeetupListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 영역(커뮤니티 글)이 모임을 연결할 때 부르는 창구. 어떤 모임을 고를 수 있는지, 고른 모임의 카드가 무엇인지를
 * 모임 영역의 모집 상태 · 확정 인원 계산으로 답한다. 연결하는 쪽은 자기 카테고리를 {@link MeetupLinkScope}로 바꿔 넘긴다.
 * 데이터를 바꾸지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class MeetupLinkService {

	private final MeetupListRepository lists;

	private final Clock clock;

	public MeetupLinkService(MeetupListRepository lists, Clock clock) {
		this.lists = lists;
		this.clock = clock;
	}

	/** 이 기준으로 고를 수 있는 모임의 카드. 나누지 않고 모두 준다. 순서는 {@link MeetupLinkScope}를 따른다. */
	public List<MeetupCardResponse> findLinkable(Long userId, MeetupLinkScope scope) {
		return lists.findLinkable(userId, scope, clock.instant(), null);
	}

	/**
	 * 이 모임을 이 기준으로 고를 수 있는지.
	 *
	 * @return 고를 수 있으면 true, 있는 모임인데 이 기준에는 맞지 않으면 false
	 * @throws BusinessException 없거나 삭제된 모임이면 {@code MEETUP_NOT_FOUND}
	 */
	public boolean isLinkable(Long userId, Long meetupId, MeetupLinkScope scope) {
		Instant now = clock.instant();
		if (lists.findCard(meetupId, now).isEmpty()) {
			throw new BusinessException(MeetupErrorCode.MEETUP_NOT_FOUND);
		}
		return !lists.findLinkable(userId, scope, now, meetupId).isEmpty();
	}

	/**
	 * 모임 하나의 카드. 취소됐으면 {@code recruitStatus}가 {@code CANCELED}이고, 삭제됐거나 없으면 null이다.
	 */
	public MeetupCardResponse findCard(Long meetupId) {
		return lists.findCard(meetupId, clock.instant()).orElse(null);
	}

}
