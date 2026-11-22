package com.itting.global.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * 세션 쿠키 {@code ITTING_SESSION}({@code docs/api-convention.md}의 "로그인").
 *
 * <p>스프링 부트의 {@code server.servlet.session.cookie.*}는 내장 서버를 띄울 때만 적용되고 테스트(MockMvc)에는
 * 적용되지 않는다. 테스트와 실제 서버가 같은 쿠키를 주도록 여기서 직접 만든다.
 */
@Configuration
public class SessionCookieConfig {

	public static final String COOKIE_NAME = "ITTING_SESSION";

	/**
	 * @param timeout 세션 유지 기간({@code spring.session.timeout}). 쿠키도 같은 기간 뒤에 브라우저에서 사라진다
	 */
	@Bean
	CookieSerializer cookieSerializer(@Value("${spring.session.timeout}") Duration timeout) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(COOKIE_NAME);
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setUseSecureCookie(true);
		serializer.setSameSite("Lax");
		serializer.setCookieMaxAge(Math.toIntExact(timeout.toSeconds()));
		return serializer;
	}

	/** Spring Session이 요청에 세션을 붙인 바로 뒤에 돌게 한다. */
	@Bean
	FilterRegistrationBean<SessionCookieRefreshFilter> sessionCookieRefreshFilter(CookieSerializer cookieSerializer) {
		FilterRegistrationBean<SessionCookieRefreshFilter> registration =
				new FilterRegistrationBean<>(new SessionCookieRefreshFilter(cookieSerializer));
		registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER + 1);
		return registration;
	}

}
