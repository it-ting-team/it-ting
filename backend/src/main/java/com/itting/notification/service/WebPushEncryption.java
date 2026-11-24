package com.itting.notification.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 푸시 내용을 암호화한다. 웹 푸시 암호화(RFC 8291)와 그 바탕인 {@code aes128gcm} 내용 부호화(RFC 8188)를 따른다.
 * 라이브러리 없이 JDK의 ECDH, HKDF-SHA256, AES-128-GCM만 쓴다.
 *
 * <p>푸시 하나는 레코드 하나다. 내용 뒤에 마지막 레코드 표시({@code 0x02})를 붙여 암호화하고, 앞에 헤더
 * (솔트 16바이트, 레코드 크기 4바이트, 키 id 길이 1바이트, 키 id)를 붙인다. 키 id는 이 푸시를 위해 새로 만든
 * 서버의 공개 키다.
 */
final class WebPushEncryption {

	/** 헤더에 적는 레코드 크기. 푸시 서비스가 받아 주는 본문의 최대 크기와 같다. */
	static final int RECORD_SIZE = 4096;

	private static final int SALT_LENGTH = 16;

	private static final int TAG_LENGTH = 16;

	/** 솔트 + 레코드 크기 + 키 id 길이 + 키 id. */
	private static final int HEADER_LENGTH = SALT_LENGTH + 4 + 1 + WebPushKeys.PUBLIC_KEY_LENGTH;

	/** 마지막 레코드 표시(1바이트)와 인증 태그를 빼고 담을 수 있는 내용의 크기. 헤더까지 본문 전체가 4,096바이트 안이어야 한다. */
	static final int MAX_PLAINTEXT = RECORD_SIZE - HEADER_LENGTH - 1 - TAG_LENGTH;

	private static final byte LAST_RECORD = 0x02;

	private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);

	private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

	private static final SecureRandom RANDOM = new SecureRandom();

	private WebPushEncryption() {
	}

	/**
	 * 푸시 내용을 암호화한다. 푸시마다 새 키와 솔트를 만든다.
	 *
	 * @param plaintext 내용. {@value #MAX_PLAINTEXT}바이트까지
	 * @param userAgentPublicKey 브라우저의 공개 키(구독의 {@code p256dh}를 푼 65바이트)
	 * @param authSecret 브라우저의 인증 값(구독의 {@code auth}를 푼 16바이트)
	 * @return {@code Content-Encoding: aes128gcm}으로 보낼 본문
	 */
	static byte[] encrypt(byte[] plaintext, byte[] userAgentPublicKey, byte[] authSecret)
			throws GeneralSecurityException {
		byte[] salt = new byte[SALT_LENGTH];
		RANDOM.nextBytes(salt);
		return encrypt(plaintext, userAgentPublicKey, authSecret, WebPushKeys.generate(), salt);
	}

	/**
	 * 서버의 키와 솔트를 정해서 암호화한다. 표준 문서의 예시 값으로 확인하는 테스트가 쓴다.
	 */
	static byte[] encrypt(byte[] plaintext, byte[] userAgentPublicKey, byte[] authSecret, KeyPair applicationServerKeys,
			byte[] salt) throws GeneralSecurityException {
		if (plaintext.length > MAX_PLAINTEXT) {
			throw new IllegalArgumentException("푸시 내용이 너무 큽니다: " + plaintext.length + "바이트");
		}
		if (salt.length != SALT_LENGTH) {
			throw new IllegalArgumentException("솔트는 16바이트여야 합니다.");
		}
		ECPublicKey userAgentKey = WebPushKeys.publicKey(userAgentPublicKey);
		byte[] applicationServerPublicKey = WebPushKeys.encode((ECPublicKey) applicationServerKeys.getPublic());

		KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
		agreement.init(applicationServerKeys.getPrivate());
		agreement.doPhase(userAgentKey, true);
		byte[] sharedSecret = agreement.generateSecret();

		// RFC 8291 3.4: 인증 값을 솔트로, ECDH 비밀을 입력으로 한 HKDF에 두 공개 키를 섞어 입력 키를 만든다.
		byte[] keyInfo = ByteBuffer.allocate(KEY_INFO_PREFIX.length + 2 * WebPushKeys.PUBLIC_KEY_LENGTH)
				.put(KEY_INFO_PREFIX)
				.put(userAgentPublicKey)
				.put(applicationServerPublicKey)
				.array();
		byte[] inputKey = hkdf(authSecret, sharedSecret, keyInfo, 32);

		// RFC 8188 2.2 ~ 2.3: 레코드 솔트로 내용 암호화 키와 nonce를 만든다. 레코드가 하나라 순번은 0이다.
		byte[] contentEncryptionKey = hkdf(salt, inputKey, CEK_INFO, 16);
		byte[] nonce = hkdf(salt, inputKey, NONCE_INFO, 12);

		byte[] record = new byte[plaintext.length + 1];
		System.arraycopy(plaintext, 0, record, 0, plaintext.length);
		record[plaintext.length] = LAST_RECORD;
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentEncryptionKey, "AES"),
				new GCMParameterSpec(TAG_LENGTH * 8, nonce));
		byte[] encrypted = cipher.doFinal(record);

		return ByteBuffer.allocate(HEADER_LENGTH + encrypted.length)
				.put(salt)
				.putInt(RECORD_SIZE)
				.put((byte) WebPushKeys.PUBLIC_KEY_LENGTH)
				.put(applicationServerPublicKey)
				.put(encrypted)
				.array();
	}

	/** HKDF-SHA256(솔트, 입력 키, info)에서 {@code length}바이트를 뽑는다. */
	private static byte[] hkdf(byte[] salt, byte[] inputKey, byte[] info, int length) throws GeneralSecurityException {
		return KDF.getInstance("HKDF-SHA256")
				.deriveData(HKDFParameterSpec.ofExtract().addIKM(inputKey).addSalt(salt).thenExpand(info, length));
	}

}
