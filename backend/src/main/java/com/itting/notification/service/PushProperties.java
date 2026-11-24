package com.itting.notification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 브라우저 푸시 설정({@code itting.push}). 값은 {@code .env}나 환경 변수로 넣는다. 키는 {@code ./gradlew vapidKeys}로 만든다.
 * 공개 키, 개인 키, 연락처 가운데 하나라도 비어 있으면 서버는 정상으로 뜨고 푸시만 보내지 않는다.
 *
 * @param publicKey VAPID 공개 키. 브라우저가 푸시 구독을 만들 때 쓰고(알림 설정이 준다) 푸시 서비스가 서명을 확인할 때 쓴다.
 *        비어 있으면 알림 설정이 null을 준다
 * @param privateKey VAPID 개인 키. 푸시를 보낼 때 서명하는 데 쓴다. 비밀값이라 응답 · 로그에 남기지 않는다
 * @param subject 푸시 서비스가 문제를 알릴 연락처. {@code mailto:팀주소} 형식
 * @param allowLocalEndpoints 서버 안쪽 주소(내 PC, 사설망)와 {@code http://} 주소로도 푸시를 보내는지. 기본은 끔이고
 *        사용자가 등록한 주소로 서버가 요청을 보내므로 서버 안쪽을 건드리지 못하게 막는다. 푸시 서비스를 흉내 내는 테스트만 켠다
 */
@ConfigurationProperties("itting.push")
public record PushProperties(String publicKey, String privateKey, String subject, boolean allowLocalEndpoints) {

	/** 푸시를 보낼 수 있게 세 값이 모두 채워져 있는지. 키의 모양이 맞는지는 보지 않는다. */
	public boolean isConfigured() {
		return hasText(publicKey) && hasText(privateKey) && hasText(subject);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
