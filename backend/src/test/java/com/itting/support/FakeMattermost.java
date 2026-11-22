package com.itting.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 테스트에서 Mattermost Incoming Webhook을 흉내 내는 작은 HTTP 서버. 실제 Mattermost로는 보내지 않는다.
 *
 * <p>주소의 마지막 부분이 답을 정한다.
 * <ul>
 * <li>{@code /hooks/ok-*}: {@code 200 ok}(보냄)</li>
 * <li>{@code /hooks/bad-*}: {@code 400}(없는 아이디나 지워진 웹후크. 실제 Mattermost와 같은 본문)</li>
 * <li>{@code /hooks/down-*}: {@code 500}</li>
 * </ul>
 * 받은 요청은 모두 기록한다.
 */
public class FakeMattermost {

	/** 받은 요청. 본문은 UTF-8로 읽는다. */
	public record Received(String hook, String contentType, String body) {
	}

	private final HttpServer server;

	private final List<Received> received = new CopyOnWriteArrayList<>();

	public FakeMattermost() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		server.createContext("/hooks/", this::handle);
		server.start();
	}

	/** 이 서버의 웹후크 주소. 예: {@code url("ok-1")} */
	public String url(String hook) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/hooks/" + hook;
	}

	/** 연결을 받지 않는 주소(연결 오류 흉내). */
	public static String refusedUrl() {
		return "http://127.0.0.1:1/hooks/refused";
	}

	public List<Received> received() {
		return List.copyOf(received);
	}

	public List<Received> receivedBy(String hook) {
		return received.stream().filter(r -> r.hook().equals(hook)).toList();
	}

	public void clear() {
		received.clear();
	}

	public void stop() {
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		String hook = exchange.getRequestURI().getPath().substring("/hooks/".length());
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		received.add(new Received(hook, exchange.getRequestHeaders().getFirst("Content-Type"), body));

		int status;
		String response;
		if (hook.startsWith("ok-")) {
			status = 200;
			response = "ok";
		}
		else if (hook.startsWith("bad-")) {
			status = 400;
			response = "{\"id\":\"web.incoming_webhook.general.app_error\",\"status_code\":400}";
		}
		else {
			status = 500;
			response = "{\"status_code\":500}";
		}
		byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

}
