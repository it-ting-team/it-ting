package com.itting.notification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 브라우저 푸시 설정({@code itting.push}).
 *
 * @param publicKey 브라우저가 푸시 구독을 만들 때 쓰는 공개 키. 비어 있으면 알림 설정이 null을 준다.
 *        키를 만들어 넣는 일은 KAN-59(브라우저 푸시)가 한다
 */
@ConfigurationProperties("itting.push")
public record PushProperties(String publicKey) {
}
