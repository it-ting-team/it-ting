package com.itting.realtime.service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis 채널({@link EventStreamBus#CHANNEL})에서 신호를 받아, 그 종류를 맡은 {@link EventStreamRelay}에 넘긴다. 신호는
 * 받은 순서대로 한 번에 하나씩 처리한다({@link EventStreamRedisConfig}).
 *
 * <p>신호 하나가 실패해도 다음 신호를 계속 처리한다. 읽을 수 없는 신호와 맡은 전달기가 없는 신호는 버린다.
 */
@Component
public class EventStreamSubscriber implements MessageListener {

	private static final Logger log = LoggerFactory.getLogger(EventStreamSubscriber.class);

	private final Map<String, EventStreamRelay> relays = new HashMap<>();

	private final JsonMapper jsonMapper;

	public EventStreamSubscriber(List<EventStreamRelay> relays, JsonMapper jsonMapper) {
		for (EventStreamRelay relay : relays) {
			for (String type : relay.signalTypes()) {
				EventStreamRelay previous = this.relays.put(type, relay);
				if (previous != null) {
					throw new IllegalStateException("신호 " + type + "을 맡은 전달기가 둘입니다.");
				}
			}
		}
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		String type = null;
		try {
			EventStreamSignal signal = jsonMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8),
					EventStreamSignal.class);
			type = signal.type();
			EventStreamRelay relay = relays.get(type);
			if (relay == null) {
				log.warn("맡은 전달기가 없는 신호를 버립니다. type={}", type);
				return;
			}
			relay.deliver(signal);
		}
		catch (RuntimeException e) {
			log.warn("실시간 소식을 전하지 못했습니다. type={}", type, e);
		}
	}

}
