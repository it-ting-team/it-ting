package com.itting.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 테스트에서 Google의 토큰 주소와 사용자 정보 주소를 흉내 내는 작은 HTTP 서버. 실제 Google로는 요청하지 않는다.
 * 브라우저가 가는 로그인 화면 주소({@link #authorizationUri()})는 부르는 곳이 없고 서버가 만든 {@code Location}을 확인하는 데만 쓴다.
 *
 * <p>테스트가 {@link #issueCode}로 인가 코드를 하나 받아 콜백에 넘기면, 서버는 그 코드를 토큰 주소에서 액세스 토큰으로
 * 바꾸고 사용자 정보 주소에서 {@code sub}와 {@code email}을 받는다. 진짜 Google처럼 다음을 확인하고, 틀리면 거절한다.
 * <ul>
 * <li>클라이언트 ID · 보안 비밀 · 콜백 주소가 서버 설정과 같다({@link #CLIENT_ID} 등. 테스트 설정이 같은 값을 넣는다)</li>
 * <li>인가 코드는 한 번만 쓸 수 있다</li>
 * <li>코드를 받을 때 시작 주소의 {@code code_challenge}를 알려 줬으면 {@code code_verifier}의 SHA-256(S256)과 같다</li>
 * <li>사용자 정보는 {@code Authorization: Bearer <액세스 토큰>}으로만 준다</li>
 * </ul>
 * 받은 토큰 요청은 모두 기록한다.
 */
public class FakeGoogle {

	/** 서버 설정({@code itting.google.*})에 넣는 값. 가짜 서버가 같은 값을 기대한다. */
	public static final String CLIENT_ID = "fake-client-id.apps.googleusercontent.com";

	public static final String CLIENT_SECRET = "fake-client-secret"; // gitleaks:allow (테스트용 가짜 값)

	public static final String REDIRECT_URI = "http://localhost:8080/api/v1/oauth/google/callback";

	private record Grant(String sub, String email, String codeChallenge) {
	}

	/** 받은 토큰 요청의 폼 값. */
	public record TokenRequest(String method, String contentType, Map<String, String> form) {
	}

	private final HttpServer server;

	private final Map<String, Grant> codes = new ConcurrentHashMap<>();

	private final Map<String, Grant> tokens = new ConcurrentHashMap<>();

	private final List<TokenRequest> tokenRequests = new CopyOnWriteArrayList<>();

	private final AtomicInteger counter = new AtomicInteger();

	private volatile int tokenFailure;

	private volatile int userinfoFailure;

	public FakeGoogle() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		server.createContext("/token", this::token);
		server.createContext("/userinfo", this::userinfo);
		server.start();
	}

	public String authorizationUri() {
		return base() + "/auth";
	}

	public String tokenUri() {
		return base() + "/token";
	}

	public String userinfoUri() {
		return base() + "/userinfo";
	}

	/**
	 * 인가 코드를 하나 발급한다. 콜백에 {@code code}로 넘긴다.
	 *
	 * @param email 사용자 정보에 담을 이메일. null이면 이메일 없이 준다
	 * @param codeChallenge 시작 주소의 {@code code_challenge}. 넘기면 토큰을 받을 때 {@code code_verifier}가 맞는지 본다. null이면 보지 않는다
	 */
	public String issueCode(String sub, String email, String codeChallenge) {
		String code = "fake-code-" + counter.incrementAndGet();
		codes.put(code, new Grant(sub, email, codeChallenge));
		return code;
	}

	/** 이 코드를 토큰 주소에서 바꾸려 하면 이 상태 코드로 답한다. 0이면 정상. */
	public void failTokenWith(int status) {
		this.tokenFailure = status;
	}

	/** 사용자 정보 주소가 이 상태 코드로 답한다. 0이면 정상. */
	public void failUserinfoWith(int status) {
		this.userinfoFailure = status;
	}

	public List<TokenRequest> tokenRequests() {
		return List.copyOf(tokenRequests);
	}

	public void clear() {
		codes.clear();
		tokens.clear();
		tokenRequests.clear();
		tokenFailure = 0;
		userinfoFailure = 0;
	}

	public void stop() {
		server.stop(0);
	}

	private String base() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private void token(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		Map<String, String> form = parseForm(body);
		tokenRequests.add(new TokenRequest(exchange.getRequestMethod(),
				exchange.getRequestHeaders().getFirst("Content-Type"), form));
		if (tokenFailure != 0) {
			respond(exchange, tokenFailure, "{\"error\":\"server_error\"}");
			return;
		}
		boolean clientOk = CLIENT_ID.equals(form.get("client_id")) && CLIENT_SECRET.equals(form.get("client_secret"));
		if (!"POST".equals(exchange.getRequestMethod()) || !clientOk) {
			respond(exchange, 401, "{\"error\":\"invalid_client\"}");
			return;
		}
		Grant grant = "authorization_code".equals(form.get("grant_type")) && form.get("code") != null
				? codes.remove(form.get("code"))
				: null;
		if (grant == null || !REDIRECT_URI.equals(form.get("redirect_uri"))
				|| (grant.codeChallenge() != null && !grant.codeChallenge().equals(challengeOf(form.get("code_verifier"))))) {
			respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
			return;
		}
		String accessToken = "fake-access-" + counter.incrementAndGet();
		tokens.put(accessToken, grant);
		respond(exchange, 200, "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":3599}");
	}

	private void userinfo(HttpExchange exchange) throws IOException {
		if (userinfoFailure != 0) {
			respond(exchange, userinfoFailure, "{\"error\":\"server_error\"}");
			return;
		}
		String authorization = exchange.getRequestHeaders().getFirst("Authorization");
		Grant grant = authorization != null && authorization.startsWith("Bearer ")
				? tokens.get(authorization.substring("Bearer ".length()))
				: null;
		if (grant == null) {
			respond(exchange, 401, "{\"error\":\"invalid_token\"}");
			return;
		}
		String email = grant.email() == null ? "" : ",\"email\":\"" + grant.email() + "\",\"email_verified\":true";
		respond(exchange, 200, "{\"sub\":\"" + grant.sub() + "\"" + email + "}");
	}

	private static void respond(HttpExchange exchange, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private static Map<String, String> parseForm(String body) {
		Map<String, String> form = new HashMap<>();
		for (String pair : body.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int equals = pair.indexOf('=');
			String name = equals < 0 ? pair : pair.substring(0, equals);
			String value = equals < 0 ? "" : pair.substring(equals + 1);
			form.put(URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
		}
		return form;
	}

	/** PKCE의 S256: {@code code_verifier}의 SHA-256을 base64url(패딩 없음)로 쓴다. */
	public static String challengeOf(String codeVerifier) {
		if (codeVerifier == null) {
			return null;
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
