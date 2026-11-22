package com.itting.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/email-verifications/check}의 본문.
 *
 * @param email 코드를 받은 이메일. 앞뒤 공백은 뗀다
 * @param code 숫자 6자리
 */
public record EmailVerificationCheckRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Pattern(regexp = "[0-9]{6}", message = "숫자 6자리여야 합니다.") String code) {

	public EmailVerificationCheckRequest {
		email = email == null ? null : email.strip();
	}

}
