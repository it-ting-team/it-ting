package com.itting.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.itting.realtime.service.EventStreamConnections;
import org.awaitility.Awaitility;

/**
 * 실시간 소식 연결({@code GET /api/v1/me/event-stream})을 실제 HTTP로 열어 흘러온 것을 모으는 테스트 도구. 브라우저의
 * {@code EventSource}처럼 줄을 읽어 소식(이름과 값)과 주석 줄을 나눈다. {@link RealServer#eventStream}으로 만든다.
 *
 * <p>연결이 열리지 않은 응답({@code 401}, {@code 403})도 담는다. 그때는 {@link #body()}가 응답 본문이다.
 */
public final class EventStreamClient implements AutoCloseable {

	private static final Duration WAIT = Duration.ofSeconds(10);

	private final HttpResponse<InputStream> response;

	private final List<Event> events = new ArrayList<>();

	private final StringBuilder raw = new StringBuilder();

	private final CountDownLatch ended = new CountDownLatch(1);

	private int pings;

	EventStreamClient(HttpResponse<InputStream> response) {
		this.response = response;
		Thread reader = new Thread(this::read, "event-stream-test-reader");
		reader.setDaemon(true);
		reader.start();
	}

	/**
	 * 테스트가 연 연결을 모두 끊고, 서버가 그 연결을 지울 때까지 기다린다. 서버는 받는 쪽이 끊은 연결을 신호를 쓰다 실패해야
	 * 알아채므로 신호를 직접 보낸다. 테스트를 마칠 때마다 부른다. 남겨 두면 다음 테스트의 신호 때나 서버가 꺼질 때 정리되면서
	 * 그 테스트와 상관없는 로그가 남는다.
	 */
	public static void closeAll(List<EventStreamClient> streams, EventStreamConnections connections) {
		streams.forEach(EventStreamClient::close);
		Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> {
			connections.heartbeat();
			return connections.isEmpty();
		});
	}

	public int status() {
		return response.statusCode();
	}

	public String contentType() {
		return response.headers().firstValue("Content-Type").orElse(null);
	}

	/**
	 * 이름이 같은 다음 소식을 꺼낸다. 아직 오지 않았으면 올 때까지 기다린다. 다른 이름의 소식은 그대로 둔다.
	 *
	 * @throws AssertionError 기다려도 오지 않으면
	 */
	public synchronized Event await(String name) throws InterruptedException {
		long deadline = System.nanoTime() + WAIT.toNanos();
		while (true) {
			for (Iterator<Event> iterator = events.iterator(); iterator.hasNext();) {
				Event event = iterator.next();
				if (event.name().equals(name)) {
					iterator.remove();
					return event;
				}
			}
			long left = deadline - System.nanoTime();
			if (left <= 0) {
				throw new AssertionError("소식 " + name + "이 오지 않았습니다. 꺼내지 않은 소식: " + events);
			}
			TimeUnit.NANOSECONDS.timedWait(this, left);
		}
	}

	/** 받았지만 아직 꺼내지 않은 소식. */
	public synchronized List<Event> pending() {
		return List.copyOf(events);
	}

	/** 지금까지 받은 연결 유지 신호(주석 줄 {@code :ping})의 수. */
	public synchronized int pings() {
		return pings;
	}

	/**
	 * 신호를 이만큼 받을 때까지 기다린다.
	 *
	 * @throws AssertionError 기다려도 오지 않으면
	 */
	public void awaitPings(int count) throws InterruptedException {
		awaitPings(count, WAIT);
	}

	/**
	 * 신호를 이만큼 받을 때까지 이 시간만큼 기다린다. 예약된 신호처럼 오래 걸리는 것을 기다릴 때 쓴다.
	 *
	 * @throws AssertionError 기다려도 오지 않으면
	 */
	public synchronized void awaitPings(int count, Duration wait) throws InterruptedException {
		long deadline = System.nanoTime() + wait.toNanos();
		while (pings < count) {
			long left = deadline - System.nanoTime();
			if (left <= 0) {
				throw new AssertionError("신호를 " + count + "번 기다렸는데 " + pings + "번 왔습니다.");
			}
			TimeUnit.NANOSECONDS.timedWait(this, left);
		}
	}

	/** 서버가 연결을 끝낼 때까지 기다린다. 끝났으면 true. */
	public boolean awaitEnded() throws InterruptedException {
		return ended.await(WAIT.toMillis(), TimeUnit.MILLISECONDS);
	}

	/** 서버가 연결을 끝냈는지(기다리지 않는다). */
	public boolean isEnded() {
		return ended.getCount() == 0;
	}

	/** 응답이 끝날 때까지 기다려 받은 글을 모두 준다. 연결이 열리지 않은 응답의 본문을 볼 때 쓴다. */
	public String body() throws InterruptedException {
		if (!awaitEnded()) {
			throw new AssertionError("응답이 끝나지 않았습니다.");
		}
		return raw();
	}

	/** 지금까지 받은 글 그대로. */
	public synchronized String raw() {
		return raw.toString();
	}

	/** 브라우저가 탭을 닫은 것처럼 이쪽에서 연결을 끊는다. */
	@Override
	public void close() {
		try {
			response.body().close();
		}
		catch (IOException e) {
			// 이미 끊긴 연결이다.
		}
	}

	private void read() {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
			String name = null;
			StringBuilder data = new StringBuilder();
			boolean comment = false;
			String line;
			while ((line = reader.readLine()) != null) {
				synchronized (this) {
					raw.append(line).append('\n');
					// 소식도 신호도 빈 줄에서 끝난다. 끝난 것만 센다(세는 때에는 그 글이 raw에 모두 들어 있다).
					if (line.isEmpty()) {
						if (name != null) {
							events.add(new Event(name, data.toString()));
						}
						else if (comment) {
							pings++;
						}
						name = null;
						comment = false;
						data.setLength(0);
					}
					else if (line.startsWith(":")) {
						comment = true;
					}
					else if (line.startsWith("event:")) {
						name = line.substring("event:".length()).strip();
					}
					else if (line.startsWith("data:")) {
						data.append(line.substring("data:".length()).stripLeading());
					}
					notifyAll();
				}
			}
		}
		catch (IOException e) {
			// 연결이 끊겼다.
		}
		finally {
			ended.countDown();
		}
	}

	/**
	 * 소식 한 건.
	 *
	 * @param data 값. JSON 글 그대로
	 */
	public record Event(String name, String data) {
	}

}
