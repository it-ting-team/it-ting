package com.itting.auth.domain;

/**
 * Google이 알려 준 계정. 로그인 · 가입 · 확인에 쓰는 값은 이 둘뿐이다(범위 {@code openid email}).
 *
 * @param sub Google 계정 식별자({@code user.google_sub})
 * @param email Google 이메일({@code user.google_email})
 */
public record GoogleAccount(String sub, String email) {
}
