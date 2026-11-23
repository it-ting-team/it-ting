package com.itting.notification.service;

import java.util.Collection;
import java.util.Map;

import com.itting.notification.domain.NotificationTargetType;

/**
 * 알림의 갈 곳의 이름과 시작 시각을 읽는다. 갈 곳의 영역이 구현해 빈으로 등록한다(갈 곳의 종류마다 하나).
 * 모임 · 모임 대화방은 07 모임, 글은 08 커뮤니티, 공지는 10 설정이 구현한다.
 * 구현이 없는 종류는 이름과 시작 시각이 null이다.
 */
public interface NotificationTargetReader {

	/** 이 구현이 읽는 갈 곳의 종류. */
	NotificationTargetType targetType();

	/**
	 * id마다의 이름과 시작 시각. 원본이 사라졌거나 보여 줄 수 없는 id는 결과에 담지 않는다.
	 */
	Map<Long, NotificationTargetSummary> read(Collection<Long> targetIds);

}
