package com.itting.auth.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 코드와 가입 진행 기록의 시간 · 횟수({@code itting.verification}).
 *
 * @param codeTtl 인증 코드의 유효 시간
 * @param resendInterval 같은 대상에게 다시 보내기까지 기다려야 하는 시간
 * @param maxAttempts 이만큼 틀리면 코드를 지운다
 * @param signupTtl 가입 진행 기록을 두는 시간
 */
@ConfigurationProperties("itting.verification")
public record VerificationProperties(Duration codeTtl, Duration resendInterval, int maxAttempts, Duration signupTtl) {
}
