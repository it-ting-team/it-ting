package com.itting.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 회원가입 테스트가 함께 쓰는 단계. 실제 API를 불러 가입을 진행한다.
 * Redis는 테스트 사이에 비우지 않으므로 이메일과 Mattermost ID는 테스트마다 새로 만든다.
 */
public class SignupSteps {

	public static final String SIGNUP_COOKIE = "ITTING_SIGNUP";

	private static final Pattern CODE = Pattern.compile("[0-9]{6}");

	private final MockMvc mockMvc;

	private final Mailpit mailpit;

	private final FakeMattermost fakeMattermost;

	public SignupSteps(MockMvc mockMvc, Mailpit mailpit, FakeMattermost fakeMattermost) {
		this.mockMvc = mockMvc;
		this.mailpit = mailpit;
		this.fakeMattermost = fakeMattermost;
	}

	/** 겹치지 않는 이메일. */
	public static String newEmail() {
		return "mole-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}

	/**
	 * 겹치지 않는 Mattermost ID(영문 소문자와 숫자).
	 * 숫자 6자리({@code 123456})를 늘 넣어, 코드를 꺼낼 때 아이디의 숫자를 코드로 잘못 읽지 않는지 매번 확인한다.
	 */
	public static String newMattermostId() {
		return "mole123456" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	public ResultActions sendEmailCode(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"" + email + "\" }"));
	}

	public ResultActions checkEmailCode(String email, String code, Cookie... cookies) throws Exception {
		var request = post("/api/v1/email-verifications/check")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"email\": \"" + email + "\", \"code\": \"" + code + "\" }");
		if (cookies.length > 0) {
			request.cookie(cookies);
		}
		return mockMvc.perform(request);
	}

	/** 이메일 인증을 마치고 가입 쿠키를 받는다. */
	public Cookie verifyEmail(String email) throws Exception {
		sendEmailCode(email).andExpect(status().isCreated());
		String code = mailpit.latestTo(email).code();
		MockHttpServletResponse response = checkEmailCode(email, code)
				.andExpect(status().isOk())
				.andReturn().getResponse();
		return signupCookie(response);
	}

	public ResultActions sendSsafyCode(Cookie signupCookie, int generation, String mattermostId) throws Exception {
		var request = post("/api/v1/ssafy-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"generation\": " + generation + ", \"mattermostId\": \"" + mattermostId + "\" }");
		if (signupCookie != null) {
			request.cookie(signupCookie);
		}
		return mockMvc.perform(request);
	}

	/**
	 * 이 아이디로 온 가장 최근 Mattermost 메시지에서 코드를 꺼낸다.
	 * 본문 전체가 아니라 {@code text}에서 찾는다. 본문의 {@code channel}에 든 아이디에도 숫자 6자리가 있을 수 있고,
	 * 서버가 {@code Map.of}로 만든 본문은 JVM마다 필드 순서가 달라 {@code channel}이 앞에 올 수 있다.
	 */
	public String lastSsafyCode(String mattermostId) {
		List<FakeMattermost.Received> messages = fakeMattermost.received().stream()
				.filter(r -> r.body().contains("\"@" + mattermostId + "\""))
				.filter(r -> r.hook().startsWith("ok-"))
				.toList();
		if (messages.isEmpty()) {
			throw new AssertionError(mattermostId + "에게 간 메시지가 없습니다.");
		}
		String text = JsonPath.read(messages.getLast().body(), "$.text");
		Matcher matcher = CODE.matcher(text);
		if (!matcher.find()) {
			throw new AssertionError("메시지에 코드가 없습니다.");
		}
		return matcher.group();
	}

	public ResultActions signUp(Cookie signupCookie, String password, String code, long campusFloorId)
			throws Exception {
		String passwordField = password == null ? "" : "\"password\": \"" + password + "\", ";
		var request = post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ " + passwordField + "\"code\": \"" + code + "\", \"campusFloorId\": " + campusFloorId
						+ ", \"hasAgreedTerms\": true, \"hasAgreedPrivacy\": true }");
		if (signupCookie != null) {
			request.cookie(signupCookie);
		}
		return mockMvc.perform(request);
	}

	/** 같은 요청을 여러 스레드에서 한꺼번에 보내고 상태 코드를 모은다(동시 요청). */
	public static List<Integer> concurrently(int count, Callable<Integer> request) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(count);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Integer>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				futures.add(executor.submit(() -> {
					start.await();
					return request.call();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> future : futures) {
				statuses.add(future.get());
			}
			return statuses;
		}
		finally {
			executor.shutdownNow();
		}
	}

	/** 응답이 준 가입 쿠키. 없으면 실패한다. */
	public static Cookie signupCookie(MockHttpServletResponse response) {
		String header = lastCookieHeader(response, SIGNUP_COOKIE);
		if (header == null) {
			throw new AssertionError("응답에 가입 쿠키가 없습니다.");
		}
		return new Cookie(SIGNUP_COOKIE, header.substring(SIGNUP_COOKIE.length() + 1, header.indexOf(';')));
	}

	/** 응답이 준 이 이름의 쿠키 헤더 가운데 마지막 것. */
	public static String lastCookieHeader(MockHttpServletResponse response, String name) {
		List<String> headers = response.getHeaders("Set-Cookie").stream()
				.filter(header -> header.startsWith(name + "="))
				.toList();
		return headers.isEmpty() ? null : headers.getLast();
	}

}
