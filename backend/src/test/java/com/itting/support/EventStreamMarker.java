package com.itting.support;

import java.util.Map;
import java.util.Set;

import com.itting.realtime.service.EventStreamBus;
import com.itting.realtime.service.EventStreamConnections;
import com.itting.realtime.service.EventStreamRelay;
import com.itting.realtime.service.EventStreamSignal;
import org.springframework.stereotype.Component;

/**
 * 실시간 소식 테스트의 표시. "이 사람은 그 소식을 받지 않는다"를 기다리지 않고 확인할 때 쓴다.
 *
 * <p>신호는 받은 순서대로 처리되고 연결마다 순서대로 나간다. 그래서 일을 일으킨 뒤에 {@link #mark}로 표시를 싣고 연결에서
 * {@link #NAME}을 받으면, 그 일의 소식은 이미 다 도착한 것이다. 그때까지 오지 않은 소식은 오지 않는다.
 */
@Component
public class EventStreamMarker implements EventStreamRelay {

	public static final String NAME = "TEST_MARKER";

	private final EventStreamBus bus;

	private final EventStreamConnections connections;

	public EventStreamMarker(EventStreamBus bus, EventStreamConnections connections) {
		this.bus = bus;
		this.connections = connections;
	}

	/** 이 계정의 연결로 표시를 보낸다. 다른 신호와 같은 길(Redis 채널)을 거친다. */
	public void mark(long userId) {
		bus.publish(NAME, Map.of("userId", userId));
	}

	@Override
	public Set<String> signalTypes() {
		return Set.of(NAME);
	}

	@Override
	public void deliver(EventStreamSignal signal) {
		connections.send(signal.arg("userId"), NAME, Map.of());
	}

}
