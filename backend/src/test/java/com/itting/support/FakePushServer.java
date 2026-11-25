package com.itting.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 테스트에서 브라우저의 푸시 서비스(FCM 등)를 흉내 내는 작은 HTTP 서버. 실제 푸시 서비스로는 보내지 않는다.
 * 받은 요청(주소, 헤더, 본문)을 모두 기록하고, 주소마다 정해 둔 상태 코드로 답한다. 정하지 않은 주소는 {@code 201 Created}다.
 */
public class FakePushServer {

	/**
	 * 받은 푸시 요청.
	 *
	 * @param path 요청 주소의 경로
	 * @param headers 헤더 이름(소문자) → 첫 값
	 */
	public record Received(String method, String path, Map<String, String> headers, byte[] body) {

		public String header(String name) {
			return headers.get(name.toLowerCase(Locale.ROOT));
		}

	}

	private final HttpServer server;

	private final List<Received> received = new CopyOnWriteArrayList<>();

	private final Map<String, Integer> statusByPath = new ConcurrentHashMap<>();

	public FakePushServer() {
		try {
			server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		server.createContext("/", this::handle);
		server.start();
	}

	/** 이 서버의 푸시 주소. 예: {@code url("/push/a")} → {@code http://localhost:포트/push/a} */
	public String url(String path) {
		return "http://localhost:" + server.getAddress().getPort() + path;
	}

	/** 이 주소로 오는 푸시에 이 상태 코드로 답한다. */
	public void respondWith(String path, int status) {
		statusByPath.put(path, status);
	}

	public List<Received> received() {
		return List.copyOf(received);
	}

	public List<Received> receivedBy(String path) {
		return received.stream().filter(r -> r.path().equals(path)).toList();
	}

	public void clear() {
		received.clear();
		statusByPath.clear();
	}

	public void stop() {
		server.stop(0);
	}

	/** 연결을 받지 않는 푸시 주소(연결 오류 흉내). 잠깐 열었다 닫은 포트다. */
	public static String refusedUrl(String path) {
		try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("localhost"))) {
			return "http://localhost:" + socket.getLocalPort() + path;
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private void handle(HttpExchange exchange) throws IOException {
		Map<String, String> headers = new TreeMap<>();
		exchange.getRequestHeaders().forEach((name, values) -> {
			if (!values.isEmpty()) {
				headers.put(name.toLowerCase(Locale.ROOT), values.getFirst());
			}
		});
		String path = exchange.getRequestURI().getPath();
		received.add(new Received(exchange.getRequestMethod(), path, headers, exchange.getRequestBody().readAllBytes()));
		exchange.sendResponseHeaders(statusByPath.getOrDefault(path, 201), -1);
		exchange.close();
	}

}
