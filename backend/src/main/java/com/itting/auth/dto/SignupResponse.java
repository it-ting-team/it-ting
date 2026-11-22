package com.itting.auth.dto;

import com.itting.auth.domain.NextStep;

/**
 * {@code POST /api/v1/users}의 {@code data}.
 *
 * @param userId 만든 계정의 id
 * @param nextStep 항상 {@code ONBOARDING}
 */
public record SignupResponse(Long userId, NextStep nextStep) {
}
