package com.itting.user.dto;

import com.itting.auth.dto.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code PATCH /api/v1/me/password}의 본문. "새 비밀번호 확인" 칸은 보내지 않는다. 두 칸이 같은지는 프론트엔드가 본다.
 *
 * @param currentPassword 지금 쓰는 비밀번호. 규칙은 검사하지 않는다(틀리면 {@code CURRENT_PASSWORD_MISMATCH})
 * @param newPassword 새 비밀번호. 비밀번호 규칙을 따른다
 */
public record PasswordChangeRequest(
		@Schema(description = "지금 쓰는 비밀번호", example = "mole1234!") @NotBlank(message = "필수 값입니다.") String currentPassword,
		@Schema(description = "새 비밀번호. 8 ~ 20자, 영문 · 숫자 · 특수기호(!@#$%)를 각각 1개 이상", example = "newmole5678@")
		@NotBlank(message = "필수 값입니다.") @ValidPassword String newPassword) {
}
