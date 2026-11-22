package com.itting.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * {@code POST /api/v1/users}의 본문. 이메일, 기수, Mattermost ID는 가입 진행 기록의 값을 쓴다.
 *
 * @param password 이메일 가입이면 필수. Google 가입이면 보내지 않는다
 * @param code Mattermost로 받은 숫자 6자리
 * @param campusFloorId 고른 캠퍼스-층의 id
 * @param hasAgreedTerms 이용약관 동의. {@code true}여야 한다
 * @param hasAgreedPrivacy 개인정보 수집 · 이용 동의. {@code true}여야 한다
 */
public record SignupRequest(
		@ValidPassword String password,
		@NotBlank @Pattern(regexp = "[0-9]{6}", message = "숫자 6자리여야 합니다.") String code,
		@NotNull @Positive Long campusFloorId,
		@NotNull @AssertTrue(message = "동의해야 합니다.") Boolean hasAgreedTerms,
		@NotNull @AssertTrue(message = "동의해야 합니다.") Boolean hasAgreedPrivacy) {
}
