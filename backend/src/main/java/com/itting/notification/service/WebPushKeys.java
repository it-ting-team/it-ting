package com.itting.notification.service;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 웹 푸시가 쓰는 P-256(secp256r1) 키를 바이트와 {@code java.security} 키로 오가게 한다.
 * 공개 키는 비압축 점 65바이트({@code 0x04 || X || Y}), 개인 키는 32바이트 숫자이고, 글로는 base64url(끝의 {@code =} 없이)이다.
 * VAPID 키와 브라우저(구독)의 키가 모두 이 모양이다.
 */
final class WebPushKeys {

	static final int PUBLIC_KEY_LENGTH = 65;

	static final int PRIVATE_KEY_LENGTH = 32;

	private static final int COORDINATE_LENGTH = 32;

	private static final ECParameterSpec P256 = p256();

	private WebPushKeys() {
	}

	/** 새 키 한 쌍. */
	static KeyPair generate() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
			generator.initialize(new ECGenParameterSpec("secp256r1"));
			return generator.generateKeyPair();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("P-256 키를 만들 수 없습니다.", e);
		}
	}

	/**
	 * 비압축 점 65바이트를 공개 키로 바꾼다. 점이 곡선 위에 없으면 거절한다.
	 *
	 * @throws IllegalArgumentException 길이 · 모양이 맞지 않거나 곡선 위의 점이 아니다
	 */
	static ECPublicKey publicKey(byte[] encoded) {
		if (encoded == null || encoded.length != PUBLIC_KEY_LENGTH || encoded[0] != 0x04) {
			throw new IllegalArgumentException("공개 키는 비압축 점 65바이트여야 합니다.");
		}
		BigInteger x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, 1 + COORDINATE_LENGTH));
		BigInteger y = new BigInteger(1, Arrays.copyOfRange(encoded, 1 + COORDINATE_LENGTH, PUBLIC_KEY_LENGTH));
		if (!isOnCurve(x, y)) {
			throw new IllegalArgumentException("공개 키가 P-256 곡선 위의 점이 아닙니다.");
		}
		try {
			return (ECPublicKey) KeyFactory.getInstance("EC")
					.generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("공개 키를 읽을 수 없습니다.", e);
		}
	}

	/**
	 * 32바이트 숫자를 개인 키로 바꾼다.
	 *
	 * @throws IllegalArgumentException 길이가 맞지 않거나 0이거나 곡선의 차수 이상이다
	 */
	static ECPrivateKey privateKey(byte[] scalar) {
		if (scalar == null || scalar.length != PRIVATE_KEY_LENGTH) {
			throw new IllegalArgumentException("개인 키는 32바이트여야 합니다.");
		}
		BigInteger s = new BigInteger(1, scalar);
		if (s.signum() == 0 || s.compareTo(P256.getOrder()) >= 0) {
			throw new IllegalArgumentException("개인 키가 허용 범위 밖입니다.");
		}
		try {
			return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(s, P256));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("개인 키를 읽을 수 없습니다.", e);
		}
	}

	/** 공개 키를 비압축 점 65바이트로. */
	static byte[] encode(ECPublicKey key) {
		byte[] encoded = new byte[PUBLIC_KEY_LENGTH];
		encoded[0] = 0x04;
		System.arraycopy(fixed(key.getW().getAffineX(), COORDINATE_LENGTH), 0, encoded, 1, COORDINATE_LENGTH);
		System.arraycopy(fixed(key.getW().getAffineY(), COORDINATE_LENGTH), 0, encoded, 1 + COORDINATE_LENGTH,
				COORDINATE_LENGTH);
		return encoded;
	}

	/** 개인 키를 32바이트 숫자로. */
	static byte[] encode(ECPrivateKey key) {
		return fixed(key.getS(), PRIVATE_KEY_LENGTH);
	}

	/** 끝의 {@code =} 없는 base64url. */
	static String base64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * base64url 글을 바이트로. 끝의 {@code =}는 있어도 없어도 된다.
	 *
	 * @throws IllegalArgumentException base64url이 아니다
	 */
	static byte[] decodeBase64Url(String text) {
		return Base64.getUrlDecoder().decode(text);
	}

	/** 부호 바이트를 떼거나 앞을 0으로 채워 정확히 {@code length}바이트로 만든다. */
	static byte[] fixed(BigInteger value, int length) {
		byte[] raw = value.toByteArray();
		byte[] fixed = new byte[length];
		if (raw.length >= length) {
			System.arraycopy(raw, raw.length - length, fixed, 0, length);
		}
		else {
			System.arraycopy(raw, 0, fixed, length - raw.length, raw.length);
		}
		return fixed;
	}

	/** {@code y² = x³ + ax + b (mod p)}. */
	private static boolean isOnCurve(BigInteger x, BigInteger y) {
		BigInteger p = ((ECFieldFp) P256.getCurve().getField()).getP();
		if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
			return false;
		}
		BigInteger left = y.multiply(y).mod(p);
		BigInteger right = x.pow(3).add(P256.getCurve().getA().multiply(x)).add(P256.getCurve().getB()).mod(p);
		return left.equals(right);
	}

	private static ECParameterSpec p256() {
		try {
			AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
			parameters.init(new ECGenParameterSpec("secp256r1"));
			return parameters.getParameterSpec(ECParameterSpec.class);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("P-256 곡선을 읽을 수 없습니다.", e);
		}
	}

}
