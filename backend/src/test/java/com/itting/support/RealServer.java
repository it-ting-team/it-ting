package com.itting.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * 실제 포트로 띄운 서버({@code RANDOM_PORT})를 HTTP로 부르는 테스트 도구. 열어 둔 연결처럼 MockMvc로 볼 수 없는 것을 확인할 때 쓴다.
 * 세션 쿠키는 {@code Secure}라 HTTP 클라이언트가 스스로 실어 보내지 않으므로, 받은 값을 {@code Cookie} 헤더로 직접 싣는다.
 */
public final class RealServer {

	private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	private final int port;

	public RealServer(int port) {
		this.port = port;
	}

	/**
	 * 이메일로 로그인한다.
	 *
	 * @return 다음 요청의 {@code Cookie} 헤더에 실을 값({@code ITTING_SESSION=...})
	 */
	public String login(String email, String password) throws IOException, InterruptedException {
		HttpResponse<String> response = send("POST", "/api/v1/sessions", null, """
				{ "email": "%s", "password": "%s" }
				""".formatted(email, password));
		if (response.statusCode() != 201) {
			throw new AssertionError("로그인하지 못했습니다. " + response.statusCode() + " " + response.body());
		}
		List<String> cookies = response.headers().allValues("Set-Cookie").stream()
				.filter(header -> header.startsWith(TestUsers.SESSION_COOKIE + "="))
				.toList();
		String cookie = cookies.getLast();
		return cookie.substring(0, cookie.indexOf(';'));
	}

	public HttpResponse<String> get(String path, String cookie) throws IOException, InterruptedException {
		return send("GET", path, cookie, null);
	}

	public HttpResponse<String> post(String path, String cookie, String json) throws IOException, InterruptedException {
		return send("POST", path, cookie, json);
	}

	/**
	 * @param cookie {@link #login}이 준 값. 로그인하지 않고 부르면 null
	 * @param json 요청 본문. 없으면 null
	 */
	public HttpResponse<String> send(String method, String path, String cookie, String json)
			throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path));
		if (cookie != null) {
			request.header("Cookie", cookie);
		}
		if (json == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		}
		else {
			request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
		}
		return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * 실시간 소식 연결을 연다. 브라우저의 {@code EventSource}처럼 {@code Accept: text/event-stream}으로 부른다. 연결이 열리지
	 * 않아도({@code 401}, {@code 403}) 응답을 담아 준다.
	 *
	 * @param cookie {@link #login}이 준 값. 로그인하지 않고 부르면 null
	 */
	public EventStreamClient eventStream(String cookie) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/v1/me/event-stream"))
				.header("Accept", "text/event-stream")
				.GET();
		if (cookie != null) {
			request.header("Cookie", cookie);
		}
		HttpResponse<InputStream> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
		return new EventStreamClient(response);
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

}
