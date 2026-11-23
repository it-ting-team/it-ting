package com.itting.notification.service;

import java.util.Optional;

/**
 * 알림 팝업의 "채팅 요청" 줄에 쓰는 값을 읽는다. 06 채팅이 구현해 빈으로 등록한다. 구현이 없으면 0과 빈 값이다.
 */
public interface ChatRequestReader {

	/** 내가 받은, 아직 답하지 않은 대화 요청의 수. 채팅의 요청 수({@code receivedCount})와 같다. */
	long countReceivedPending(Long userId);

	/** 위 요청 가운데 가장 최근에 요청을 보낸 사람의 계정 id. 요청이 없으면 빈 값. */
	Optional<Long> findLatestRequesterId(Long userId);

}
