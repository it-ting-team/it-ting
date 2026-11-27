package com.itting.user.dto;

import com.itting.user.domain.WithdrawalReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/me/withdraw}의 본문. "동의" 체크는 보내지 않는다. 프론트엔드가 체크해야 버튼을 켠다.
 *
 * @param password 지금 쓰는 비밀번호. 비밀번호가 있는 계정은 반드시 보낸다(없으면 {@code INVALID_INPUT}). Google로만 가입한 계정은
 *        보내지 않는다
 * @param reason 탈퇴 사유. 고르지 않았으면 null. 목록에 없는 값이면 {@code INVALID_INPUT}
 */
public record WithdrawRequest(
		@Schema(description = "지금 쓰는 비밀번호. 비밀번호가 있는 계정은 반드시 보낸다. Google로만 가입한 계정은 보내지 않는다",
				example = "mole1234!", nullable = true) String password,
		@Schema(description = "탈퇴 사유. 고르지 않았으면 null이거나 보내지 않는다", example = "COURSE_ENDED", nullable = true)
		WithdrawalReason reason) {
}
