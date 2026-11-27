package com.itting.realtime.service;

import org.springframework.context.event.EventListener;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.stereotype.Component;

/**
 * 로그인 세션이 지워지거나 만료되면 그 세션으로 연 실시간 소식 연결을 끊는다({@code docs/api/06-chat.md}의 "계정이 로그아웃하거나
 * 정지되거나 탈퇴하면 연결을 끊는다"). 셋 모두 세션을 지우는 일이다. 비밀번호 변경 · 재설정으로 지워진 다른 기기의 세션도 같다.
 *
 * <p>이 이벤트는 Spring Session이 Redis의 알림을 받아 낸다. 그래서 세션을 지운 서버가 아니어도 모든 서버에 온다.
 */
@Component
class EventStreamSessionListener {

	private final EventStreamConnections connections;

	EventStreamSessionListener(EventStreamConnections connections) {
		this.connections = connections;
	}

	@EventListener
	void onSessionEnded(SessionDestroyedEvent event) {
		connections.closeSession(event.getSessionId());
	}

}
