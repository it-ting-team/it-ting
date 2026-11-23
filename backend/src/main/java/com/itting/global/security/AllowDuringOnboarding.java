package com.itting.global.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 온보딩을 마치지 않은 계정도 부를 수 있는 API에 붙인다. API 문서의 "온보딩 중에도"에 해당한다.
 * 클래스에 붙이면 그 컨트롤러의 API 모두다.
 *
 * <p>붙이지 않은 API를 온보딩을 마치지 않은 계정이 부르면 {@code 403 ONBOARDING_INCOMPLETE}다({@link AccountStepInterceptor}).
 * 로그인 없이 부르는 API({@code SecurityConfig.PUBLIC_API})에도 붙인다. 로그인한 사람이 불러도 막지 않기 위해서다.
 */
@Documented
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowDuringOnboarding {
}
