package com.itting.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Google 로그인 · 확인 테스트가 함께 쓰는 단계. 실제 시작 주소와 콜백 주소를 부르고, Google은 {@link FakeGoogle}이 맡는다.
 * 브라우저가 하는 일(Location을 따라가고 쿠키를 다시 실어 보내기)은 테스트가 직접 한다.
 */
public class GoogleSteps {

	public static final String OAUTH_COOKIE = "ITTING_OAUTH";

	/**
	 * Google에 보낸 시도 하나.
	 *
	 * @param response 시작 주소의 응답
	 * @param location 응답의 {@code Location}(Google 로그인 화면 주소)
	 * @param state {@code Location}의 {@code state}
	 * @param codeChallenge {@code Location}의 {@code code_challenge}
	 * @param cookie 응답이 준 {@code ITTING_OAUTH} 쿠키. 콜백에 실어 보낸다
	 */
	public record Attempt(MockHttpServletResponse response, String location, String state, String codeChallenge,
			Cookie cookie) {
	}

	private final MockMvc mockMvc;

	private final FakeGoogle fakeGoogle;

	public GoogleSteps(MockMvc mockMvc, FakeGoogle fakeGoogle) {
		this.mockMvc = mockMvc;
		this.fakeGoogle = fakeGoogle;
	}

	/** {@code GET /api/v1/oauth/google}를 불러 시도를 시작한다. */
	public Attempt startLogin() throws Exception {
		return attemptOf(mockMvc.perform(get("/api/v1/oauth/google")).andExpect(status().isFound())
				.andReturn().getResponse());
	}

	/** {@code GET /api/v1/oauth/google/verify?purpose=...}를 불러 확인을 시작한다. 로그인한 세션 쿠키를 싣는다. */
	public Attempt startVerify(Cookie session, String purpose) throws Exception {
		return attemptOf(verify(session, purpose).andExpect(status().isFound()).andReturn().getResponse());
	}

	public ResultActions verify(Cookie session, String purpose) throws Exception {
		var request = get("/api/v1/oauth/google/verify");
		if (purpose != null) {
			request.param("purpose", purpose);
		}
		if (session != null) {
			request.cookie(session);
		}
		return mockMvc.perform(request);
	}

	/** 이 시도의 {@code code_challenge}를 아는 인가 코드를 발급한다. 가짜 Google이 {@code code_verifier}를 확인한다. */
	public String codeFor(Attempt attempt, String sub, String email) {
		return fakeGoogle.issueCode(sub, email, attempt.codeChallenge());
	}

	/** Google이 브라우저를 돌려보낸 것처럼 콜백을 부른다. 시도의 쿠키와 {@code state}를 싣고, 더 실을 쿠키(세션 등)를 받는다. */
	public ResultActions callback(Attempt attempt, String code, Cookie... cookies) throws Exception {
		return callback(attempt.state(), code, null, attempt.cookie(), cookies);
	}

	/** 같은 콜백을 값 하나하나 정해 부른다. {@code stateCookie}가 null이면 쿠키를 싣지 않는다. */
	public ResultActions callback(String state, String code, String error, Cookie stateCookie, Cookie... cookies)
			throws Exception {
		var request = get("/api/v1/oauth/google/callback");
		if (state != null) {
			request.param("state", state);
		}
		if (code != null) {
			request.param("code", code);
		}
		if (error != null) {
			request.param("error", error);
		}
		if (stateCookie != null) {
			request.cookie(stateCookie);
		}
		if (cookies.length > 0) {
			request.cookie(cookies);
		}
		return mockMvc.perform(request);
	}

	/** 시작부터 콜백까지 이어서 부른다. 이 계정으로 Google에서 돌아온 것으로 한다. */
	public ResultActions loginWith(String sub, String email, Cookie... cookies) throws Exception {
		Attempt attempt = startLogin();
		return callback(attempt, codeFor(attempt, sub, email), cookies);
	}

	private static Attempt attemptOf(MockHttpServletResponse response) {
		String location = response.getHeader("Location");
		assertThat(location).isNotNull();
		String header = SignupSteps.lastCookieHeader(response, OAUTH_COOKIE);
		Cookie cookie = header == null ? null
				: new Cookie(OAUTH_COOKIE, header.substring(OAUTH_COOKIE.length() + 1, header.indexOf(';')));
		Map<String, String> query = query(location);
		return new Attempt(response, location, query.get("state"), query.get("code_challenge"), cookie);
	}

	/** 주소의 쿼리를 디코딩해 이름 → 값으로 준다. 같은 이름이 여러 번이면 첫 값이다. */
	public static Map<String, String> query(String location) {
		MultiValueMap<String, String> raw = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
		Map<String, String> query = new LinkedHashMap<>();
		raw.forEach((name, values) -> query.put(name, UriUtils.decode(values.getFirst(), StandardCharsets.UTF_8)));
		return query;
	}

	/** 주소에서 쿼리를 뺀 앞부분. */
	public static String withoutQuery(String location) {
		URI uri = URI.create(location);
		return uri.getScheme() + "://" + uri.getAuthority() + uri.getRawPath();
	}

	/** 응답의 {@code Location}. */
	public static String location(MockHttpServletResponse response) {
		return response.getHeader("Location");
	}

	/** 응답이 이 이름의 쿠키를 주지 않았다. */
	public static boolean hasCookie(MockHttpServletResponse response, String name) {
		List<String> headers = response.getHeaders("Set-Cookie");
		return headers.stream().anyMatch(header -> header.startsWith(name + "="));
	}

	/** 세션 쿠키에서 Spring Session의 세션 id를 꺼낸다(쿠키 값은 세션 id를 base64로 쓴 것이다). */
	public static String sessionId(Cookie sessionCookie) {
		return new String(Base64.getDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
	}

}
