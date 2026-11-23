package com.itting.user.dto;

import java.time.Instant;

/**
 * {@code POST /api/v1/nicknames/check}의 응답. 성공이면 쓸 수 있는 닉네임이고 지금부터 이 계정 몫이다.
 *
 * @param expiresAt 맡아 둔 것이 풀리는 시각. 확인한 시각에 5분을 더한 값
 */
public record NicknameCheckResponse(Instant expiresAt) {
}
