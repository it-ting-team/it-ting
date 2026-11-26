package com.itting.realtime.service;

import java.util.Set;

/**
 * 신호를 받아 브라우저로 보낼 소식을 만드는 전달기. 소식을 만드는 영역이 구현한다(채팅, 알림).
 *
 * <p>영역은 일이 생기면 {@link EventStreamBus#publish}로 신호를 싣는다. 신호는 Redis를 거쳐 모든 서버에 오고, 서버마다
 * 이 전달기가 자기 서버에 붙어 있는 받는 사람의 값만 계산해 {@link EventStreamConnections#send}로 보낸다.
 */
public interface EventStreamRelay {

	/** 이 전달기가 맡는 신호의 종류. */
	Set<String> signalTypes();

	/**
	 * 신호 한 건을 처리한다. 받는 사람이 이 서버에 붙어 있지 않으면 아무것도 하지 않는다. 신호는 받은 순서대로 한 번에 하나씩 온다.
	 */
	void deliver(EventStreamSignal signal);

}
