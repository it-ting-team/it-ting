package com.itting.notification.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 푸시를 받는 브라우저 쪽의 일을 흉내 낸다(RFC 8291 3.4). 보내는 쪽({@link WebPushEncryption})이 JDK의 HKDF를 쓰므로,
 * 여기서는 HMAC으로 HKDF를 따로 만들어 같은 결과가 나오는지 본다.
 */
final class WebPushTestSupport {

	private WebPushTestSupport() {
	}

	/** 브라우저의 키 한 쌍과 인증 값. 구독이 담는 값과 같은 모양이다. */
	record Browser(KeyPair keys, byte[] publicKey, byte[] authSecret) {

		static Browser create() {
			KeyPair keys = WebPushKeys.generate();
			byte[] authSecret = new byte[16];
			new java.security.SecureRandom().nextBytes(authSecret);
			return new Browser(keys, WebPushKeys.encode((ECPublicKey) keys.getPublic()), authSecret);
		}

		String p256dh() {
			return WebPushKeys.base64Url(publicKey);
		}

		String auth() {
			return WebPushKeys.base64Url(authSecret);
		}

		byte[] decrypt(byte[] body) throws GeneralSecurityException {
			return WebPushTestSupport.decrypt(body, (ECPrivateKey) keys.getPrivate(), publicKey, authSecret);
		}

	}

	/**
	 * 받은 본문을 푼다. 헤더를 읽고, 서버의 공개 키와 ECDH로 비밀을 만들어 키와 nonce를 뽑는다. 레코드 하나만 다룬다.
	 *
	 * @return 마지막 레코드 표시를 뗀 내용
	 */
	static byte[] decrypt(byte[] body, ECPrivateKey userAgentPrivateKey, byte[] userAgentPublicKey, byte[] authSecret)
			throws GeneralSecurityException {
		ByteBuffer buffer = ByteBuffer.wrap(body);
		byte[] salt = new byte[16];
		buffer.get(salt);
		int recordSize = buffer.getInt();
		int keyIdLength = buffer.get() & 0xFF;
		byte[] keyId = new byte[keyIdLength];
		buffer.get(keyId);
		byte[] ciphertext = new byte[buffer.remaining()];
		buffer.get(ciphertext);
		if (recordSize < ciphertext.length) {
			throw new GeneralSecurityException("레코드가 하나가 아닙니다.");
		}

		KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
		agreement.init(userAgentPrivateKey);
		agreement.doPhase(WebPushKeys.publicKey(keyId), true);
		byte[] sharedSecret = agreement.generateSecret();

		byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), userAgentPublicKey, keyId);
		byte[] inputKey = hkdf(authSecret, sharedSecret, keyInfo, 32);
		byte[] contentEncryptionKey = hkdf(salt, inputKey, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
		byte[] nonce = hkdf(salt, inputKey, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(contentEncryptionKey, "AES"), new GCMParameterSpec(128, nonce));
		byte[] record = cipher.doFinal(ciphertext);

		int end = record.length;
		while (end > 0 && record[end - 1] == 0) {
			end--;
		}
		if (end == 0 || record[end - 1] != 0x02) {
			throw new GeneralSecurityException("마지막 레코드 표시(0x02)가 없습니다.");
		}
		return Arrays.copyOf(record, end - 1);
	}

	/** HMAC-SHA256으로 만든 HKDF(RFC 5869). */
	static byte[] hkdf(byte[] salt, byte[] inputKey, byte[] info, int length) throws GeneralSecurityException {
		byte[] pseudoRandomKey = hmac(salt, inputKey);
		byte[] output = new byte[0];
		byte[] block = new byte[0];
		for (byte counter = 1; output.length < length; counter++) {
			block = hmac(pseudoRandomKey, concat(block, info, new byte[] { counter }));
			output = concat(output, block);
		}
		return Arrays.copyOf(output, length);
	}

	static byte[] concat(byte[]... parts) {
		int length = 0;
		for (byte[] part : parts) {
			length += part.length;
		}
		byte[] joined = new byte[length];
		int offset = 0;
		for (byte[] part : parts) {
			System.arraycopy(part, 0, joined, offset, part.length);
			offset += part.length;
		}
		return joined;
	}

	private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(data);
	}

}
