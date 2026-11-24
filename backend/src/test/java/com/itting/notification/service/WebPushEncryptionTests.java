package com.itting.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;

import com.itting.notification.service.WebPushTestSupport.Browser;
import org.junit.jupiter.api.Test;

/**
 * 푸시 내용의 암호화(RFC 8291, RFC 8188). 표준 문서 부록 A의 예시 값으로 같은 결과가 나오는지 보고, 받는 쪽(브라우저)을
 * 따로 만들어 풀어 본다.
 */
class WebPushEncryptionTests {

	// RFC 8291 부록 A의 예시 값
	private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";

	private static final String APPLICATION_SERVER_PRIVATE_KEY = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";

	private static final String APPLICATION_SERVER_PUBLIC_KEY =
			"BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";

	private static final String USER_AGENT_PRIVATE_KEY = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";

	private static final String USER_AGENT_PUBLIC_KEY =
			"BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";

	private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";

	private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";

	private static final String ENCRYPTED_BODY = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vC"
			+ "YLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

	@Test
	void 표준_문서_예시의_값으로_암호화하면_예시와_같은_본문이_나온다() throws Exception {
		KeyPair applicationServerKeys = new KeyPair(WebPushKeys.publicKey(decode(APPLICATION_SERVER_PUBLIC_KEY)),
				WebPushKeys.privateKey(decode(APPLICATION_SERVER_PRIVATE_KEY)));

		byte[] body = WebPushEncryption.encrypt(PLAINTEXT.getBytes(StandardCharsets.US_ASCII),
				decode(USER_AGENT_PUBLIC_KEY), decode(AUTH_SECRET), applicationServerKeys, decode(SALT));

		assertThat(WebPushKeys.base64Url(body)).isEqualTo(ENCRYPTED_BODY);
	}

	@Test
	void 표준_문서_예시의_본문을_브라우저_쪽에서_풀면_원래_내용이다() throws Exception {
		byte[] plaintext = WebPushTestSupport.decrypt(decode(ENCRYPTED_BODY),
				WebPushKeys.privateKey(decode(USER_AGENT_PRIVATE_KEY)), decode(USER_AGENT_PUBLIC_KEY),
				decode(AUTH_SECRET));

		assertThat(new String(plaintext, StandardCharsets.US_ASCII)).isEqualTo(PLAINTEXT);
	}

	@Test
	void 표준_문서_예시의_키_파생_값이_예시와_같다() throws Exception {
		// 받는 쪽 도우미의 HKDF(HMAC으로 만듦)가 표준 문서의 값을 내는지 본다. 보내는 쪽은 JDK의 HKDF를 쓰므로 둘이 따로 맞는 셈이다.
		byte[] userAgentPublicKey = decode(USER_AGENT_PUBLIC_KEY);
		byte[] applicationServerPublicKey = decode(APPLICATION_SERVER_PUBLIC_KEY);
		javax.crypto.KeyAgreement agreement = javax.crypto.KeyAgreement.getInstance("ECDH");
		agreement.init(WebPushKeys.privateKey(decode(APPLICATION_SERVER_PRIVATE_KEY)));
		agreement.doPhase(WebPushKeys.publicKey(userAgentPublicKey), true);
		byte[] sharedSecret = agreement.generateSecret();

		byte[] keyInfo = WebPushTestSupport.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
				userAgentPublicKey, applicationServerPublicKey);
		byte[] inputKey = WebPushTestSupport.hkdf(decode(AUTH_SECRET), sharedSecret, keyInfo, 32);
		byte[] cek = WebPushTestSupport.hkdf(decode(SALT), inputKey,
				"Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
		byte[] nonce = WebPushTestSupport.hkdf(decode(SALT), inputKey,
				"Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

		assertThat(WebPushKeys.base64Url(sharedSecret)).isEqualTo("kyrL1jIIOHEzg3sM2ZWRHDRB62YACZhhSlknJ672kSs");
		assertThat(WebPushKeys.base64Url(inputKey)).isEqualTo("S4lYMb_L0FxCeq0WhDx813KgSYqU26kOyzWUdsXYyrg");
		assertThat(WebPushKeys.base64Url(cek)).isEqualTo("oIhVW04MRdy2XN9CiKLxTg");
		assertThat(WebPushKeys.base64Url(nonce)).isEqualTo("4h_95klXJ5E_qnoN");
	}

	@Test
	void 새_키와_솔트로_암호화한_본문은_헤더가_표준대로이고_브라우저가_푼다() throws Exception {
		Browser browser = Browser.create();
		byte[] plaintext = "{\"id\":7201,\"type\":\"POST_COMMENTED\"}".getBytes(StandardCharsets.UTF_8);

		byte[] body = WebPushEncryption.encrypt(plaintext, browser.publicKey(), browser.authSecret());

		ByteBuffer header = ByteBuffer.wrap(body);
		header.position(16);
		assertThat(header.getInt()).as("레코드 크기").isEqualTo(4096);
		assertThat(header.get() & 0xFF).as("키 id 길이").isEqualTo(65);
		byte[] keyId = new byte[65];
		header.get(keyId);
		assertThat(keyId[0]).as("비압축 점 표시").isEqualTo((byte) 0x04);
		assertThat(WebPushKeys.publicKey(keyId)).isInstanceOf(ECPublicKey.class);
		assertThat(body).hasSize(86 + plaintext.length + 1 + 16);
		assertThat(browser.decrypt(body)).isEqualTo(plaintext);
	}

	@Test
	void 푸시마다_솔트와_서버_키가_달라_같은_내용도_본문이_다르다() throws Exception {
		Browser browser = Browser.create();
		byte[] plaintext = "같은 내용".getBytes(StandardCharsets.UTF_8);

		byte[] first = WebPushEncryption.encrypt(plaintext, browser.publicKey(), browser.authSecret());
		byte[] second = WebPushEncryption.encrypt(plaintext, browser.publicKey(), browser.authSecret());

		assertThat(Arrays.copyOfRange(first, 0, 16)).as("솔트").isNotEqualTo(Arrays.copyOfRange(second, 0, 16));
		assertThat(Arrays.copyOfRange(first, 21, 86)).as("서버의 공개 키").isNotEqualTo(Arrays.copyOfRange(second, 21, 86));
		assertThat(first).isNotEqualTo(second);
		assertThat(browser.decrypt(first)).isEqualTo(plaintext);
		assertThat(browser.decrypt(second)).isEqualTo(plaintext);
	}

	@Test
	void 담을_수_있는_가장_큰_내용은_본문이_4096바이트이고_그보다_크면_거절한다() throws Exception {
		Browser browser = Browser.create();
		byte[] largest = new byte[WebPushEncryption.MAX_PLAINTEXT];
		Arrays.fill(largest, (byte) 'a');

		byte[] body = WebPushEncryption.encrypt(largest, browser.publicKey(), browser.authSecret());

		assertThat(WebPushEncryption.MAX_PLAINTEXT).isEqualTo(3993);
		assertThat(body).hasSize(4096);
		assertThat(browser.decrypt(body)).isEqualTo(largest);
		assertThatThrownBy(() -> WebPushEncryption.encrypt(new byte[WebPushEncryption.MAX_PLAINTEXT + 1],
				browser.publicKey(), browser.authSecret())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 곡선_위의_점이_아닌_브라우저_키는_거절한다() {
		byte[] authSecret = new byte[16];
		byte[] notOnCurve = new byte[65];
		notOnCurve[0] = 0x04;
		notOnCurve[32] = 1;
		notOnCurve[64] = 1;
		byte[] tooShort = new byte[33];

		assertThatThrownBy(() -> WebPushEncryption.encrypt(new byte[1], notOnCurve, authSecret))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WebPushEncryption.encrypt(new byte[1], tooShort, authSecret))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 본문이_바뀌었거나_다른_브라우저의_키로는_풀리지_않는다() throws Exception {
		Browser browser = Browser.create();
		Browser other = Browser.create();
		byte[] body = WebPushEncryption.encrypt("비밀".getBytes(StandardCharsets.UTF_8), browser.publicKey(),
				browser.authSecret());
		byte[] tampered = body.clone();
		tampered[tampered.length - 1] ^= 0x01;

		assertThatThrownBy(() -> browser.decrypt(tampered)).isInstanceOf(AEADBadTagException.class);
		assertThatThrownBy(() -> other.decrypt(body)).isInstanceOf(AEADBadTagException.class);
	}

	@Test
	void 키를_바이트로_바꿨다_되돌려도_같다() {
		KeyPair pair = WebPushKeys.generate();
		byte[] publicBytes = WebPushKeys.encode((ECPublicKey) pair.getPublic());
		byte[] privateBytes = WebPushKeys.encode((ECPrivateKey) pair.getPrivate());

		assertThat(publicBytes).hasSize(65);
		assertThat(privateBytes).hasSize(32);
		assertThat(WebPushKeys.encode(WebPushKeys.publicKey(publicBytes))).isEqualTo(publicBytes);
		assertThat(WebPushKeys.encode(WebPushKeys.privateKey(privateBytes))).isEqualTo(privateBytes);
	}

	private static byte[] decode(String base64Url) {
		return WebPushKeys.decodeBase64Url(base64Url);
	}

}
