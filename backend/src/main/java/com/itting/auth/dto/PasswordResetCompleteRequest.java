package com.itting.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/password-resets/{token}/complete}의 본문.
 *
 * @param password 새 비밀번호. 비밀번호 규칙을 따른다
 */
public record PasswordResetCompleteRequest(@NotBlank(message = "필수 값입니다.") @ValidPassword String password) {
}
