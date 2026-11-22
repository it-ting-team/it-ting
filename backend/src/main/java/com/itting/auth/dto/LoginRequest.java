package com.itting.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/sessions}의 본문.
 *
 * @param email 로그인 이메일
 * @param password 비밀번호. 규칙은 검사하지 않는다(틀리면 {@code LOGIN_FAILED})
 */
public record LoginRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank String password) {
}
