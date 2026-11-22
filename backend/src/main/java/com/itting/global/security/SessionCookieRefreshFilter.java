package com.itting.global.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 세션이 살아 있는 요청마다 세션 쿠키를 다시 줘서 브라우저의 쿠키 만료도 마지막 사용 뒤 14일로 늘린다.
 *
 * <p>Redis의 세션은 요청마다 만료가 늘어나지만, Spring Session은 세션 id가 바뀔 때만 쿠키를 준다.
 * 그대로 두면 쿠키는 로그인한 지 14일 뒤에 브라우저에서 사라진다.
 *
 * <p>이 요청에서 세션을 지우면(로그아웃) Spring Session이 응답 끝에 쿠키를 지우는 헤더를 더 붙인다.
 * 브라우저는 같은 이름의 쿠키 헤더를 순서대로 적용하므로 지우는 쪽이 남는다.
 */
public class SessionCookieRefreshFilter extends OncePerRequestFilter {

	private final CookieSerializer cookieSerializer;

	public SessionCookieRefreshFilter(CookieSerializer cookieSerializer) {
		this.cookieSerializer = cookieSerializer;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getRequestedSessionId() != null) {
			HttpSession session = request.getSession(false);
			if (session != null && !session.isNew()) {
				cookieSerializer.writeCookieValue(new CookieValue(request, response, session.getId()));
			}
		}
		chain.doFilter(request, response);
	}

}
