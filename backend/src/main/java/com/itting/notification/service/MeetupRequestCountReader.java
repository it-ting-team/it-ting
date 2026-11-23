package com.itting.notification.service;

/**
 * 알림 팝업의 "모임 참여 요청" 수를 읽는다. 07 모임이 구현해 빈으로 등록한다. 구현이 없으면 0이다.
 */
public interface MeetupRequestCountReader {

	/**
	 * 내가 연 모임에 온, 아직 처리하지 않은 참여 신청의 수. 취소 · 삭제된 모임과 모집 마감이 지난 모임의 신청은 세지 않는다
	 * ({@code docs/api/09-notification.md}의 "알림과 요청 수").
	 */
	long countPendingApplications(Long hostId);

}
