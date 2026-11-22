package com.itting.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/email-verifications}의 본문.
 *
 * @param email 가입할 이메일. 앞뒤 공백은 뗀다
 */
public record EmailVerificationRequest(@NotBlank @Email @Size(max = 254) String email) {

	public EmailVerificationRequest {
		email = email == null ? null : email.strip();
	}

}
