package com.itting.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 푸시를 보낸 서버를 밝히는 VAPID 서명(RFC 8292). 만든 JWT를 공개 키로 확인하고 주장(aud, exp, sub)이 맞는지 본다.
 */
class VapidSignerTests {

	private static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");

	private static final String SUBJECT = "mailto:team@example.com";

	private static final Pattern AUTHORIZATION = Pattern.compile("vapid t=([A-Za-z0-9_-]+)[.]([A-Za-z0-9_-]+)[.]([A-Za-z0-9_-]+), k=([A-Za-z0-9_-]+)");

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private final KeyPair keys = WebPushKeys.generate();

	private final String publicKey = WebPushKeys.base64Url(WebPushKeys.encode((ECPublicKey) keys.getPublic()));

	private final String privateKey = WebPushKeys.base64Url(WebPushKeys.encode((ECPrivateKey) keys.getPrivate()));

	@Test
	void Authorization_헤더는_vapid_t와_k_모양이고_k는_공개_키_그대로다() throws Exception {
		VapidSigner signer = new VapidSigner(publicKey, privateKey, SUBJECT, clock, jsonMapper);

		String header = signer.authorization(URI.create("https://fcm.googleapis.com/fcm/send/c1KrmpTuRm8:APA91bHexample"));

		Matcher matcher = AUTHORIZATION.matcher(header);
		assertThat(matcher.matches()).as(header).isTrue();
		assertThat(matcher.group(4)).isEqualTo(publicKey);
	}

	@Test
	void JWT는_ES256_헤더와_aud_exp_sub_주장을_담는다() throws Exception {
		VapidSigner signer = new VapidSigner(publicKey, privateKey, SUBJECT, clock, jsonMapper);

		String jwt = jwtOf(signer.authorization(URI.create("https://fcm.googleapis.com/fcm/send/abc")));

		String[] parts = jwt.split("[.]");
		assertThat(new String(WebPushKeys.decodeBase64Url(parts[0]), StandardCharsets.UTF_8))
				.isEqualTo("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
		JsonNode claims = jsonMapper.readTree(WebPushKeys.decodeBase64Url(parts[1]));
		assertThat(claims.get("aud").asString()).isEqualTo("https://fcm.googleapis.com");
		assertThat(claims.get("exp").asLong()).isEqualTo(NOW.plus(VapidSigner.LIFETIME).getEpochSecond());
		assertThat(VapidSigner.LIFETIME.toHours()).as("표준은 24시간을 넘기지 못하게 한다").isEqualTo(12);
		assertThat(claims.get("sub").asString()).isEqualTo(SUBJECT);
		assertThat(claims.size()).isEqualTo(3);
	}

	@Test
	void 서명은_64바이트이고_공개_키로_확인되며_내용이_바뀌면_확인되지_않는다() throws Exception {
		VapidSigner signer = new VapidSigner(publicKey, privateKey, SUBJECT, clock, jsonMapper);
		String jwt = jwtOf(signer.authorization(URI.create("https://updates.push.services.mozilla.com/wpush/v2/abc")));
		String[] parts = jwt.split("[.]");
		byte[] signature = WebPushKeys.decodeBase64Url(parts[2]);

		assertThat(signature).hasSize(64);
		assertThat(verify(keys.getPublic(), parts[0] + "." + parts[1], signature)).isTrue();
		assertThat(verify(keys.getPublic(), parts[0] + "." + parts[1] + "x", signature)).isFalse();
		assertThat(verify(WebPushKeys.generate().getPublic(), parts[0] + "." + parts[1], signature))
				.as("다른 키로는 확인되지 않는다").isFalse();
	}

	@Test
	void aud는_푸시_주소의_출처이고_기본_포트는_뺀다() {
		assertThat(VapidSigner.audience(URI.create("https://fcm.googleapis.com/fcm/send/abc")))
				.isEqualTo("https://fcm.googleapis.com");
		assertThat(VapidSigner.audience(URI.create("https://web.push.apple.com:443/abc")))
				.isEqualTo("https://web.push.apple.com");
		assertThat(VapidSigner.audience(URI.create("https://push.example.com:8443/abc")))
				.isEqualTo("https://push.example.com:8443");
		assertThat(VapidSigner.audience(URI.create("http://localhost:9000/push/1"))).isEqualTo("http://localhost:9000");
	}

	@Test
	void 푸시_주소마다_aud가_바뀌고_exp는_시계를_따른다() throws Exception {
		Instant later = NOW.plusSeconds(3600);
		VapidSigner signer = new VapidSigner(publicKey, privateKey, SUBJECT, Clock.fixed(later, ZoneOffset.UTC),
				jsonMapper);

		JsonNode first = claimsOf(signer.authorization(URI.create("https://fcm.googleapis.com/a")));
		JsonNode second = claimsOf(signer.authorization(URI.create("https://web.push.apple.com/b")));

		assertThat(first.get("aud").asString()).isEqualTo("https://fcm.googleapis.com");
		assertThat(second.get("aud").asString()).isEqualTo("https://web.push.apple.com");
		assertThat(first.get("exp").asLong()).isEqualTo(later.plus(VapidSigner.LIFETIME).getEpochSecond());
	}

	@Test
	void 공개_키와_개인_키가_한_쌍이_아니면_만들_수_없고_메시지에_키가_없다() {
		String otherPrivateKey = WebPushKeys.base64Url(WebPushKeys.encode((ECPrivateKey) WebPushKeys.generate().getPrivate()));

		assertThatThrownBy(() -> new VapidSigner(publicKey, otherPrivateKey, SUBJECT, clock, jsonMapper))
				.isInstanceOf(IllegalArgumentException.class)
				.satisfies(e -> assertThat(e.getMessage()).doesNotContain(publicKey).doesNotContain(otherPrivateKey));
	}

	@Test
	void 키의_모양이_틀렸거나_연락처가_비어_있으면_만들_수_없다() {
		assertThatThrownBy(() -> new VapidSigner("not base64 !!", privateKey, SUBJECT, clock, jsonMapper))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new VapidSigner(publicKey, "AAAA", SUBJECT, clock, jsonMapper))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new VapidSigner("AAAA", privateKey, SUBJECT, clock, jsonMapper))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new VapidSigner(publicKey, privateKey, "  ", clock, jsonMapper))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 키_만들기는_공개_키와_개인_키_두_줄을_주고_서로_짝이다() {
		String[] lines = VapidKeyGenerator.generate();

		assertThat(lines).hasSize(2);
		assertThat(lines[0]).matches("ITTING_PUSH_PUBLIC_KEY=[A-Za-z0-9_-]{87}");
		assertThat(lines[1]).matches("ITTING_PUSH_PRIVATE_KEY=[A-Za-z0-9_-]{43}");
		String generatedPublicKey = lines[0].substring(lines[0].indexOf('=') + 1);
		String generatedPrivateKey = lines[1].substring(lines[1].indexOf('=') + 1);
		assertThat(WebPushKeys.decodeBase64Url(generatedPublicKey)).hasSize(65).startsWith((byte) 0x04);
		assertThat(WebPushKeys.decodeBase64Url(generatedPrivateKey)).hasSize(32);
		// 만든 키로 서명기를 만들 수 있다(한 쌍이 아니면 만들 때 거절한다)
		assertThat(new VapidSigner(generatedPublicKey, generatedPrivateKey, SUBJECT, clock, jsonMapper)).isNotNull();
		assertThat(VapidKeyGenerator.generate()[1]).as("호출마다 새 키").isNotEqualTo(lines[1]);
	}

	private JsonNode claimsOf(String authorization) {
		return jsonMapper.readTree(WebPushKeys.decodeBase64Url(jwtOf(authorization).split("[.]")[1]));
	}

	private static String jwtOf(String authorization) {
		Matcher matcher = AUTHORIZATION.matcher(authorization);
		assertThat(matcher.matches()).as(authorization).isTrue();
		return matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3);
	}

	private static boolean verify(java.security.PublicKey key, String signingInput, byte[] signature)
			throws Exception {
		Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
		verifier.initVerify(key);
		verifier.update(signingInput.getBytes(StandardCharsets.US_ASCII));
		return verifier.verify(signature);
	}

}
