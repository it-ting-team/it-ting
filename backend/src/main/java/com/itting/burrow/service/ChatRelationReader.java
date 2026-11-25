package com.itting.burrow.service;

import com.itting.burrow.dto.BurrowVisitResponse.ChatResponse;

/**
 * 남의 땅굴 조회의 대화 버튼 상태를 읽는다. 06 채팅이 구현한다. 어떤 요청이 살아 있는지는 채팅만 안다.
 * ({@code docs/api/05-explore-visit.md}의 "대화 버튼의 상태")
 */
public interface ChatRelationReader {

	/** 나({@code viewerId})와 땅굴 주인({@code ownerId}) 사이의 대화 상태. 두 id는 서로 다르다. */
	ChatResponse read(Long viewerId, Long ownerId);

}
