package com.itting.auth.dto;

import java.time.Instant;

/**
 * 인증 코드를 보내는 API(이메일 · SSAFY)의 {@code data}.
 *
 * @param expiresAt 코드가 만료되는 시각. 프론트엔드가 남은 시간을 표시한다
 */
public record VerificationCodeSentResponse(Instant expiresAt) {
}
