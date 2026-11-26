package com.itting.realtime.service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 실시간 소식의 신호 채널을 듣는 설정. 로그인 세션용 채널(Spring Session)과 따로 둔다.
 */
@Configuration
class EventStreamRedisConfig {

	/** 처리를 기다리는 신호의 한도. 넘으면 그 신호는 버린다(프론트엔드가 다시 연결하거나 화면을 열 때 조회 API로 맞춘다). */
	static final int QUEUE_CAPACITY = 10_000;

	private static final Logger log = LoggerFactory.getLogger(EventStreamRedisConfig.class);

	/**
	 * 신호를 처리하는 스레드 하나. 하나라서 신호가 Redis에 실린 순서대로 처리된다. 같은 대화방에 두 사람이 거의 동시에 보내면
	 * 실린 순서가 메시지 id의 순서와 다를 수 있다(소식의 메시지에 id가 있어 받는 쪽이 맞춘다). 값을 계산하는 일(DB 조회)만
	 * 여기서 하고, 연결에 쓰는 일은 연결마다 따로 한다({@link EventStreamConnection}).
	 */
	@Bean(destroyMethod = "shutdownNow")
	ExecutorService eventStreamRelayExecutor() {
		return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY),
				task -> {
					Thread thread = new Thread(task, "event-stream-relay");
					thread.setDaemon(true);
					return thread;
				},
				(task, executor) -> {
					if (!executor.isShutdown()) {
						log.warn("처리를 기다리는 실시간 소식의 신호가 너무 많아 버립니다.");
					}
				});
	}

	@Bean
	RedisMessageListenerContainer eventStreamListenerContainer(RedisConnectionFactory connectionFactory,
			EventStreamSubscriber subscriber, ExecutorService eventStreamRelayExecutor) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.setTaskExecutor(eventStreamRelayExecutor);
		// 채널을 붙드는 일이 신호를 처리하는 스레드를 차지하지 않게 따로 둔다.
		container.setSubscriptionExecutor(new SimpleAsyncTaskExecutor("event-stream-subscription-"));
		container.addMessageListener(subscriber, new ChannelTopic(EventStreamBus.CHANNEL));
		return container;
	}

}
