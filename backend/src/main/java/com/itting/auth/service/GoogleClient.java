package com.itting.auth.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import com.itting.auth.domain.GoogleAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Google에서 로그인한 사람의 계정을 받아 온다. 콜백이 받은 {@code code}를 토큰 주소에서 액세스 토큰으로 바꾸고, 그 토큰으로
 * 사용자 정보 주소를 불러 {@code sub}와 {@code email}을 받는다(범위 {@code openid email}). ID 토큰의 서명은 보지 않는다.
 *
 * <p>코드, 토큰, 보안 비밀, 응답 본문은 로그에 남기지 않는다. 상태 코드와 예외 종류만 남긴다.
 */
@Service
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleClient {

	private static final Logger log = LoggerFactory.getLogger(GoogleClient.class);

	/** {@code user.google_sub}, {@code user.google_email}의 칼럼 길이. */
	private static final int MAX_SUB_LENGTH = 255;

	private static final int MAX_EMAIL_LENGTH = 254;

	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT = new ParameterizedTypeReference<>() {
	};

	private final GoogleProperties properties;

	private final RestClient restClient;

	public GoogleClient(GoogleProperties properties) {
		this.properties = properties;
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	/**
	 * @param code Google이 콜백으로 준 인가 코드
	 * @param codeVerifier 시작할 때 만든 PKCE의 {@code code_verifier}
	 * @throws GoogleClientException 통신하지 못했거나, Google이 거절했거나, 계정 식별자 · 이메일이 없는 응답
	 */
	public GoogleAccount fetchAccount(String code, String codeVerifier) {
		try {
			String accessToken = exchangeCode(code, codeVerifier);
			return fetchUserInfo(accessToken);
		}
		catch (RestClientResponseException e) {
			log.warn("Google 요청 실패: 상태 {}", e.getStatusCode().value());
			throw new GoogleClientException("Google이 " + e.getStatusCode().value() + "로 답했습니다.");
		}
		catch (RestClientException | IllegalArgumentException e) {
			// 예외 메시지에 주소나 요청 내용이 들어 있을 수 있어 종류만 남긴다.
			log.warn("Google 통신 실패: {}", e.getClass().getSimpleName());
			throw new GoogleClientException("Google과 통신하지 못했습니다.", e);
		}
	}

	private String exchangeCode(String code, String codeVerifier) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("code", code);
		form.add("redirect_uri", properties.redirectUri());
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("code_verifier", codeVerifier);
		Map<String, Object> token = restClient.post()
				.uri(URI.create(properties.tokenUri()))
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.accept(MediaType.APPLICATION_JSON)
				.body(form)
				.retrieve()
				.body(JSON_OBJECT);
		String accessToken = text(token, "access_token");
		if (accessToken == null) {
			throw new GoogleClientException("Google이 액세스 토큰을 주지 않았습니다.");
		}
		return accessToken;
	}

	private GoogleAccount fetchUserInfo(String accessToken) {
		Map<String, Object> info = restClient.get()
				.uri(URI.create(properties.userinfoUri()))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(JSON_OBJECT);
		String sub = text(info, "sub");
		String email = text(info, "email");
		if (sub == null || email == null || sub.length() > MAX_SUB_LENGTH || email.length() > MAX_EMAIL_LENGTH) {
			throw new GoogleClientException("Google이 계정 식별자와 이메일을 주지 않았습니다.");
		}
		return new GoogleAccount(sub, email);
	}

	/** 문자열 값이고 비어 있지 않으면 그 값, 아니면 null. */
	private static String text(Map<String, Object> json, String name) {
		if (json == null || !(json.get(name) instanceof String value) || value.isBlank()) {
			return null;
		}
		return value;
	}

}
