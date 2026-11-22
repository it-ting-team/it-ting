package com.itting.auth.dto;

import java.time.Instant;

/**
 * {@code ACCOUNT_SUSPENDED}의 {@code error.detail}. 정지 팝업에 보여 준다.
 *
 * @param reason 정지 사유
 * @param endsAt 정지가 끝나는 시각. 남은 기간은 프론트엔드가 계산한다
 */
public record SuspensionDetailResponse(String reason, Instant endsAt) {
}
