package com.itting.notification.service;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * 브라우저 푸시(VAPID)용 키 한 쌍을 만들어 {@code .env}에 붙여 넣을 두 줄로 출력한다. 파일은 만들지 않는다.
 * {@code backend/}에서 {@code ./gradlew -q vapidKeys}로 실행한다.
 */
public final class VapidKeyGenerator {

	private VapidKeyGenerator() {
	}

	public static void main(String[] args) {
		for (String line : generate()) {
			System.out.println(line);
		}
	}

	/** {@code ITTING_PUSH_PUBLIC_KEY=...}와 {@code ITTING_PUSH_PRIVATE_KEY=...} 두 줄. */
	static String[] generate() {
		KeyPair pair = WebPushKeys.generate();
		return new String[] {
				"ITTING_PUSH_PUBLIC_KEY=" + WebPushKeys.base64Url(WebPushKeys.encode((ECPublicKey) pair.getPublic())),
				"ITTING_PUSH_PRIVATE_KEY=" + WebPushKeys.base64Url(WebPushKeys.encode((ECPrivateKey) pair.getPrivate())) };
	}

}
