package com.itting.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 서버가 사용자를 보내는 프론트엔드 주소. 비밀번호 재설정 메일의 링크와 Google 로그인 뒤 돌아갈 주소에 쓴다.
 *
 * 없으면 서버가 뜨지 않는다(운영에서 링크가 엉뚱한 곳을 가리키지 않게).
 *
 * @param frontendUrl {@code itting.frontend-url}. 끝에 {@code /}를 붙이지 않는다. 예: {@code http://localhost:3000}
 */
@Validated
@ConfigurationProperties("itting")
public record FrontendProperties(@NotBlank String frontendUrl) {
}
