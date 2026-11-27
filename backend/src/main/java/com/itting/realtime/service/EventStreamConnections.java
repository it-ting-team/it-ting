package com.itting.realtime.service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.itting.auth.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이 서버에 붙어 있는 실시간 소식 연결의 등록부({@code GET /api/v1/me/event-stream}, {@code docs/api/06-chat.md}).
 *
 * <ul>
 * <li>계정마다 연결이 여럿일 수 있다(탭, 기기). 소식은 그 계정의 모든 연결로 간다.
 * <li>연결은 그것을 연 로그인 세션에 묶인다. 세션이 지워지면(로그아웃, 정지, 탈퇴, 비밀번호 변경 · 재설정) 그 세션의 연결을 끊는다
 * ({@link EventStreamSessionListener}). 세션이 지워졌다는 알림을 놓쳤을 때를 대비해, 신호를 보낼 때마다 세션이 살아 있는지도
 * 다시 본다.
 * <li>연결하자마자, 그 뒤로는 설정한 간격마다 연결을 유지하는 신호(주석 줄 {@code :ping})를 보낸다. 브라우저가 끊은 연결은 이 신호를
 * 쓰다 실패해 지워진다.
 * <li>서버가 꺼질 때 모든 연결을 끊고, 끊는 처리가 끝날 때까지 잠깐 기다린다. 브라우저는 다시 연결한다.
 * </ul>
 *
 * 다른 서버에 붙어 있는 연결은 모른다. 서버 사이의 전달은 {@link EventStreamBus}가 한다.
 */
@Component
public class EventStreamConnections {

	/** 서버가 꺼질 때 연결을 끊는 처리가 끝나기를 기다리는 시간. 넘으면 기다리지 않고 꺼지는 일을 이어 간다. */
	static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);

	private static final Logger log = LoggerFactory.getLogger(EventStreamConnections.class);

	private final ConcurrentHashMap<Long, Set<EventStreamConnection>> byUser = new ConcurrentHashMap<>();

	private final SessionService sessionService;

	private final JsonMapper jsonMapper;

	private volatile boolean stopped;

	public EventStreamConnections(SessionService sessionService, JsonMapper jsonMapper) {
		this.sessionService = sessionService;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * 연결을 연다.
	 *
	 * @param sessionId 이 요청의 로그인 세션. 이 세션이 지워지면 연결도 끊긴다
	 * @return 컨트롤러가 그대로 돌려줄 응답
	 */
	public SseEmitter open(Long userId, String sessionId) {
		EventStreamConnection connection = new EventStreamConnection(userId, sessionId, this::remove);
		byUser.compute(userId, (id, connections) -> {
			Set<EventStreamConnection> updated = connections == null ? ConcurrentHashMap.newKeySet() : connections;
			updated.add(connection);
			return updated;
		});
		connection.sendPing();
		connection.start();
		// 등록하는 사이에 세션이 지워졌으면 세션이 지워졌다는 이벤트가 이 연결을 보지 못하고 지나갔다. 서버가 꺼지는 중일 때도 같다.
		boolean alive;
		try {
			alive = sessionService.isAlive(sessionId);
		}
		catch (RuntimeException e) {
			// 요청이 실패로 끝나므로 등록해 둔 연결을 남기지 않는다.
			connection.close();
			throw e;
		}
		if (stopped || !alive) {
			connection.close();
		}
		return connection.emitter();
	}

	/** 이 서버에 붙어 있는 연결이 하나도 없는지. */
	public boolean isEmpty() {
		return byUser.isEmpty();
	}

	/** 이 계정의 연결이 이 서버에 하나라도 있는지. */
	public boolean isConnected(Long userId) {
		return byUser.containsKey(userId);
	}

	/** 이 계정들 가운데 이 서버에 붙어 있는 계정. */
	public Set<Long> connectedAmong(Collection<Long> userIds) {
		Set<Long> connected = new HashSet<>();
		for (Long userId : userIds) {
			if (byUser.containsKey(userId)) {
				connected.add(userId);
			}
		}
		return connected;
	}

	/** 이 계정이 이 서버에 열어 둔 연결 수. */
	public int countOf(Long userId) {
		Set<EventStreamConnection> connections = byUser.get(userId);
		return connections == null ? 0 : connections.size();
	}

	/**
	 * 이 계정이 이 서버에 열어 둔 모든 연결로 소식 한 건을 보낸다. 붙어 있지 않으면 아무것도 하지 않는다.
	 *
	 * @param event 소식의 이름
	 * @param data 소식의 값. 응답과 같은 규칙의 JSON으로 바꿔 보낸다
	 */
	public void send(Long userId, String event, Object data) {
		Set<EventStreamConnection> connections = byUser.get(userId);
		if (connections == null) {
			return;
		}
		String json = jsonMapper.writeValueAsString(data);
		for (EventStreamConnection connection : connections) {
			connection.sendEvent(event, json);
		}
	}

	/** 이 로그인 세션으로 연 연결을 끊는다. */
	public void closeSession(String sessionId) {
		for (EventStreamConnection connection : all()) {
			if (connection.sessionId().equals(sessionId)) {
				connection.close();
			}
		}
	}

	/**
	 * 연결을 유지하는 신호를 모든 연결에 보낸다. 보내기 전에 세션이 지워진 연결을 끊는다.
	 */
	@Scheduled(fixedRateString = "${itting.event-stream.heartbeat-interval}",
			initialDelayString = "${itting.event-stream.heartbeat-interval}")
	public void heartbeat() {
		List<EventStreamConnection> connections = all();
		closeEndedSessions(connections);
		connections.forEach(EventStreamConnection::sendPing);
	}

	/**
	 * 세션이 지워졌는데 남아 있는 연결을 끊는다. 세션이 지워졌다는 것은 Redis의 알림으로 오는데({@link EventStreamSessionListener}),
	 * Redis만 다시 뜨거나 알림을 받는 연결이 잠깐 끊기면 놓칠 수 있다. 놓쳐도 다음 신호 때 여기서 끊긴다.
	 */
	private void closeEndedSessions(List<EventStreamConnection> connections) {
		Map<String, Boolean> aliveBySession = new HashMap<>();
		try {
			for (EventStreamConnection connection : connections) {
				if (!aliveBySession.computeIfAbsent(connection.sessionId(), sessionService::isAlive)) {
					connection.close();
				}
			}
		}
		catch (RuntimeException e) {
			// 세션 저장소를 읽지 못했다. 살아 있는지 알 수 없으므로 끊지 않고 다음 신호 때 다시 본다.
			log.warn("실시간 소식 연결의 세션을 확인하지 못했습니다. 원인={}", e.getClass().getSimpleName());
		}
	}

	/**
	 * 서버가 꺼지기 시작할 때 모든 연결을 끊는다. 응답을 끝내는 처리는 다른 스레드에서 이어지고, 그 처리가 로그인 세션(Redis)을
	 * 읽는다. 그 사이에 Redis 연결이 먼저 닫히면 끊는 처리가 실패하므로, 끝날 때까지 여기서 기다린다.
	 */
	@EventListener(ContextClosedEvent.class)
	void closeAll() {
		stopped = true;
		List<EventStreamConnection> connections = all();
		connections.forEach(EventStreamConnection::close);
		long deadline = System.nanoTime() + SHUTDOWN_WAIT.toNanos();
		try {
			for (EventStreamConnection connection : connections) {
				if (!connection.awaitFinished(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
					log.warn("실시간 소식 연결을 끊는 처리가 {}초 안에 끝나지 않았습니다. 기다리지 않고 서버를 끕니다.",
							SHUTDOWN_WAIT.toSeconds());
					return;
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private List<EventStreamConnection> all() {
		return byUser.values().stream().flatMap(Set::stream).toList();
	}

	private void remove(EventStreamConnection connection) {
		byUser.computeIfPresent(connection.userId(), (id, connections) -> {
			connections.remove(connection);
			return connections.isEmpty() ? null : connections;
		});
	}

}
