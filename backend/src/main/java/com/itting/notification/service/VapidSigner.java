package com.itting.notification.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/**
 * 푸시 서비스에 "이 서버가 보낸 푸시"임을 밝히는 VAPID 서명(RFC 8292). 푸시를 보낼 때마다 ES256 JWT를 만들어
 * {@code Authorization: vapid t=<JWT>, k=<공개 키>} 헤더에 싣는다.
 *
 * <p>JWT의 {@code aud}는 푸시 주소의 출처(스킴 + 호스트 + 포트), {@code exp}는 12시간 뒤(표준의 한도는 24시간),
 * {@code sub}는 푸시 서비스가 문제를 알릴 연락처({@code mailto:} 또는 {@code https:})다.
 * 키는 {@code web-push generate-vapid-keys}와 같은 모양이다: 공개 키는 비압축 점 65바이트, 개인 키는 32바이트 숫자이고
 * 둘 다 base64url(끝의 {@code =} 없이)이다.
 */
final class VapidSigner {

	/** JWT의 유효 시간. 표준은 24시간을 넘기지 못하게 한다. */
	static final Duration LIFETIME = Duration.ofHours(12);

	private static final String HEADER = "{\"typ\":\"JWT\",\"alg\":\"ES256\"}";

	private final String publicKey;

	private final ECPrivateKey privateKey;

	private final String subject;

	private final Clock clock;

	private final JsonMapper jsonMapper;

	/**
	 * @param publicKey 공개 키(base64url). 푸시 서비스가 서명을 확인할 때 쓰고, 브라우저가 구독을 만들 때도 같은 값을 쓴다
	 * @param privateKey 개인 키(base64url)
	 * @param subject 연락처. {@code mailto:}나 {@code https:}로 시작한다
	 * @throws IllegalArgumentException 키의 모양이 틀렸거나 두 키가 한 쌍이 아니거나 연락처가 비었다. 메시지에 키는 담지 않는다
	 */
	VapidSigner(String publicKey, String privateKey, String subject, Clock clock, JsonMapper jsonMapper) {
		this.publicKey = publicKey;
		this.subject = subject;
		this.clock = clock;
		this.jsonMapper = jsonMapper;
		if (subject == null || subject.isBlank()) {
			throw new IllegalArgumentException("VAPID 연락처가 비어 있습니다.");
		}
		ECPublicKey parsedPublicKey;
		try {
			byte[] publicBytes = WebPushKeys.decodeBase64Url(publicKey);
			parsedPublicKey = WebPushKeys.publicKey(publicBytes);
			this.privateKey = WebPushKeys.privateKey(WebPushKeys.decodeBase64Url(privateKey));
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("VAPID 키를 읽을 수 없습니다: " + e.getClass().getSimpleName(), e);
		}
		verifyPair(parsedPublicKey);
	}

	/** {@code Authorization} 헤더의 값. */
	String authorization(URI endpoint) throws GeneralSecurityException {
		return "vapid t=" + jwt(audience(endpoint)) + ", k=" + publicKey;
	}

	/** 푸시 주소의 출처(스킴 + 호스트 + 포트). 스킴의 기본 포트(https 443, http 80)는 적지 않는다. */
	static String audience(URI endpoint) {
		String scheme = endpoint.getScheme().toLowerCase(Locale.ROOT);
		String origin = scheme + "://" + endpoint.getHost().toLowerCase(Locale.ROOT);
		int port = endpoint.getPort();
		boolean defaultPort = port == -1 || (scheme.equals("https") && port == 443)
				|| (scheme.equals("http") && port == 80);
		return defaultPort ? origin : origin + ":" + port;
	}

	String jwt(String audience) throws GeneralSecurityException {
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("aud", audience);
		claims.put("exp", clock.instant().plus(LIFETIME).getEpochSecond());
		claims.put("sub", subject);
		String signingInput = base64Url(HEADER.getBytes(StandardCharsets.UTF_8)) + "."
				+ base64Url(jsonMapper.writeValueAsBytes(claims));
		Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
		signature.initSign(privateKey);
		signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
		return signingInput + "." + base64Url(signature.sign());
	}

	/** 공개 키와 개인 키가 한 쌍이 아니면 푸시 서비스가 모든 푸시를 거절한다. 서명해 보고 확인해 미리 알아낸다. */
	private void verifyPair(ECPublicKey parsedPublicKey) {
		try {
			byte[] probe = "vapid-key-check".getBytes(StandardCharsets.US_ASCII);
			Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
			signer.initSign(privateKey);
			signer.update(probe);
			byte[] signed = signer.sign();
			Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
			verifier.initVerify(parsedPublicKey);
			verifier.update(probe);
			if (!verifier.verify(signed)) {
				throw new IllegalArgumentException("VAPID 공개 키와 개인 키가 한 쌍이 아닙니다.");
			}
		}
		catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("VAPID 키를 확인할 수 없습니다: " + e.getClass().getSimpleName(), e);
		}
	}

	private static String base64Url(byte[] bytes) {
		return WebPushKeys.base64Url(bytes);
	}

}
