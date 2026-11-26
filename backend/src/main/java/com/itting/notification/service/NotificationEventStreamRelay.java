package com.itting.notification.service;

import java.util.Map;
import java.util.Set;

import com.itting.realtime.service.EventStreamBus;
import com.itting.realtime.service.EventStreamConnections;
import com.itting.realtime.service.EventStreamRelay;
import com.itting.realtime.service.EventStreamSignal;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 알림의 실시간 소식 {@code NOTIFICATION_UNREAD_CHANGED}를 브라우저로 보낸다({@code docs/api/09-notification.md}의
 * "실시간 소식"). 새 알림이 생겼거나, 알림을 읽었거나, 알림 설정을 바꿔 빨간 점이 달라졌을 때다.
 *
 * <p>알림 서비스가 커밋 뒤에 내는 {@link NotificationUnreadChangedEvent}를 받아 신호를 싣고, 신호가 돌아오면 그 계정이 이 서버에
 * 붙어 있을 때만 값을 계산해 보낸다. 값은 안 읽은 수 API와 같다. 새 알림의 내용은 담지 않는다.
 */
@Component
class NotificationEventStreamRelay implements EventStreamRelay {

	static final String UNREAD_CHANGED = "NOTIFICATION_UNREAD_CHANGED";

	private static final String USER_ID = "userId";

	private final EventStreamBus bus;

	private final EventStreamConnections connections;

	private final NotificationQueryService notificationQueryService;

	NotificationEventStreamRelay(EventStreamBus bus, EventStreamConnections connections,
			NotificationQueryService notificationQueryService) {
		this.bus = bus;
		this.connections = connections;
		this.notificationQueryService = notificationQueryService;
	}

	@EventListener
	void onUnreadChanged(NotificationUnreadChangedEvent event) {
		bus.publish(UNREAD_CHANGED, Map.of(USER_ID, event.userId()));
	}

	@Override
	public Set<String> signalTypes() {
		return Set.of(UNREAD_CHANGED);
	}

	@Override
	public void deliver(EventStreamSignal signal) {
		Long userId = signal.arg(USER_ID);
		if (!connections.isConnected(userId)) {
			return;
		}
		connections.send(userId, UNREAD_CHANGED, notificationQueryService.unreadCount(userId));
	}

}
