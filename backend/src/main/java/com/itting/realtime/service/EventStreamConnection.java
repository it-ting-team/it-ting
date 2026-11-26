package com.itting.realtime.service;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * 브라우저 하나와 열어 둔 연결. 보낼 것을 줄에 넣으면 이 연결의 스레드가 순서대로 쓴다.
 *
 * <ul>
 * <li>쓰는 일을 연결마다 따로 해서, 받는 쪽이 느리거나 멈춘 연결 하나가 다른 사람의 소식을 막지 않는다.
 * <li>줄이 차면 받는 쪽이 따라오지 못하는 것이므로 연결을 끊는다. 쓰다가 실패해도 끊는다. 프론트엔드는 다시 연결해 조회 API로
 * 화면을 맞춘다(끊긴 동안의 소식은 다시 보내지 않는다).
 * </ul>
 */
final class EventStreamConnection {

	/** 보내길 기다리는 것의 한도. */
	static final int QUEUE_CAPACITY = 256;

	private static final Frame CLOSE = new Frame(null, null);

	private final Long userId;

	private final String sessionId;

	private final Consumer<EventStreamConnection> onClosed;

	/** 시간 제한을 두지 않는다. 끊긴 연결은 신호를 쓰다 실패해 알아챈다. */
	private final SseEmitter emitter = new SseEmitter(0L);

	private final BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

	private final AtomicBoolean closing = new AtomicBoolean();

	/** 서블릿 컨테이너가 이 요청의 처리를 모두 마쳤을 때 풀린다. */
	private final CountDownLatch finished = new CountDownLatch(1);

	/**
	 * @param onClosed 연결이 끝났을 때 한 번 부른다(등록부에서 지우기)
	 */
	EventStreamConnection(Long userId, String sessionId, Consumer<EventStreamConnection> onClosed) {
		this.userId = userId;
		this.sessionId = sessionId;
		this.onClosed = onClosed;
	}

	Long userId() {
		return userId;
	}

	String sessionId() {
		return sessionId;
	}

	SseEmitter emitter() {
		return emitter;
	}

	/** 쓰는 스레드를 띄운다. 컨트롤러가 응답을 시작하기 전에 넣은 것은 응답이 시작될 때 함께 나간다. */
	void start() {
		// 브라우저가 스스로 끊으면 다음에 쓸 때 실패해 알아챈다. 컨테이너가 먼저 알려 주면 그때 줄을 닫는다.
		emitter.onCompletion(() -> {
			close();
			finished.countDown();
		});
		emitter.onError(e -> close());
		Thread.ofVirtual().name("event-stream-" + userId).start(this::write);
	}

	/** 소식 한 건. {@code data}는 JSON 한 줄이다. */
	boolean sendEvent(String name, String json) {
		return offer(new Frame(name, json));
	}

	/** 연결을 유지하는 신호. 주석 줄이라 브라우저의 {@code EventSource}가 화면 코드로 넘기지 않는다. */
	boolean sendPing() {
		return offer(new Frame(null, "ping"));
	}

	/** 줄에 남은 것을 버리고 연결을 끝낸다. 여러 번 불러도 한 번만 한다. */
	void close() {
		if (closing.compareAndSet(false, true)) {
			queue.clear();
			queue.offer(CLOSE);
		}
	}

	/**
	 * 끊은 뒤 서블릿 컨테이너가 이 요청의 처리를 모두 마칠 때까지 기다린다. 응답을 끝내는 일은 {@link #close()}를 부른 스레드가
	 * 아니라 쓰는 스레드와 컨테이너의 스레드에서 이어지기 때문이다.
	 *
	 * @return 그 시간 안에 마쳤으면 true
	 */
	boolean awaitFinished(long timeout, TimeUnit unit) throws InterruptedException {
		return finished.await(timeout, unit);
	}

	private boolean offer(Frame frame) {
		if (closing.get()) {
			return false;
		}
		if (queue.offer(frame)) {
			return true;
		}
		close();
		return false;
	}

	private void write() {
		boolean failed = false;
		try {
			while (true) {
				Frame frame = queue.take();
				if (frame == CLOSE) {
					break;
				}
				emitter.send(frame.toEvent());
			}
		}
		catch (IOException | RuntimeException e) {
			// 받는 쪽이 연결을 끊었다. 응답을 끝내는 일은 서블릿 컨테이너가 한다.
			failed = true;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			closing.set(true);
			onClosed.accept(this);
			if (!failed) {
				complete();
			}
		}
	}

	private void complete() {
		try {
			emitter.complete();
		}
		catch (RuntimeException e) {
			// 이미 끝난 응답이다.
		}
	}

	/**
	 * 보낼 것 하나.
	 *
	 * @param name 소식의 이름. null이면 주석 줄이다
	 * @param text 소식의 값(JSON) 또는 주석의 글
	 */
	private record Frame(String name, String text) {

		SseEventBuilder toEvent() {
			if (name == null) {
				return SseEmitter.event().comment(text);
			}
			return SseEmitter.event().name(name).data(text);
		}

	}

}
