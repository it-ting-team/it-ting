package com.itting.auth.dto;

import com.itting.auth.domain.NextStep;

/**
 * {@code POST /api/v1/sessions}의 {@code data}.
 *
 * @param userId 로그인한 계정의 id
 * @param nextStep 다음에 갈 화면
 */
public record LoginResponse(Long userId, NextStep nextStep) {
}
