package com.itting.realtime.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실시간 소식의 신호를 Redis 채널에 싣는다. 서버가 여러 대여도 받는 사람이 어느 서버에 붙어 있든 소식이 간다. 채널을 듣는 쪽은
 * {@link EventStreamSubscriber}다.
 *
 * <p>신호를 싣지 못해도 예외를 내지 않는다. 신호는 일을 처리한 트랜잭션이 커밋된 뒤에 싣기 때문에, 여기서 예외가 나가면 이미 끝난
 * 요청이 실패로 답한다. 놓친 소식은 프론트엔드가 다시 연결하거나 화면을 열 때 조회 API로 맞춘다.
 */
@Component
public class EventStreamBus {

	/** 신호가 오가는 Redis 채널. */
	public static final String CHANNEL = "itting:event-stream";

	private static final Logger log = LoggerFactory.getLogger(EventStreamBus.class);

	private final StringRedisTemplate redis;

	private final JsonMapper jsonMapper;

	public EventStreamBus(StringRedisTemplate redis, JsonMapper jsonMapper) {
		this.redis = redis;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * @param type 신호의 종류. 이 종류를 맡은 {@link EventStreamRelay}가 받는다
	 * @param args 그 일을 가리키는 id
	 */
	public void publish(String type, Map<String, Long> args) {
		try {
			redis.convertAndSend(CHANNEL, jsonMapper.writeValueAsString(new EventStreamSignal(type, args)));
		}
		catch (RuntimeException e) {
			log.warn("실시간 소식의 신호를 싣지 못했습니다. type={}, 원인={}", type, e.getClass().getSimpleName());
		}
	}

}
