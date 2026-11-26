package com.itting.auth.service;

import com.itting.user.domain.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * 로그인 세션을 만들고 지운다. 세션은 Spring Session이 Redis에 두고, 쿠키 {@code ITTING_SESSION}으로 가리킨다.
 *
 * <p>세션에는 계정 id만 넣는다. 인증의 주인(principal)이 계정 id라서, 컨트롤러는
 * {@code @AuthenticationPrincipal Long userId}로 받는다. Spring Session은 이 값으로 세션을 색인하므로
 * 한 계정의 세션을 모두 찾아 지울 수 있다.
 */
@Service
public class SessionService {

	private final SecurityContextRepository securityContextRepository;

	private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	public SessionService(SecurityContextRepository securityContextRepository,
			FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
		this.securityContextRepository = securityContextRepository;
		this.sessionRepository = sessionRepository;
	}

	/**
	 * 새 세션을 만들어 로그인시킨다. 요청에 이전 세션이 있으면 버리고 새 id로 만든다(세션 고정 공격 방지).
	 */
	public void start(HttpServletRequest request, HttpServletResponse response, Long userId, UserRole role) {
		HttpSession previous = request.getSession(false);
		if (previous != null) {
			previous.invalidate();
		}
		request.getSession(true);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				userId, null, AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}

	/** 그 계정의 세션을 모든 기기에서 지운다(비밀번호 재설정, 정지, 탈퇴). 세션의 주인 이름이 계정 id다. */
	public void endAll(Long userId) {
		sessionRepository.findByPrincipalName(String.valueOf(userId)).keySet().forEach(sessionRepository::deleteById);
	}

	/**
	 * 그 계정의 세션 가운데 지금 요청의 세션만 남기고 지운다(비밀번호 변경). 다른 기기는 로그아웃되고 지금 쓰는 기기는 그대로다.
	 * 요청에 세션이 없으면 모두 지운다.
	 */
	public void endOthers(Long userId, HttpServletRequest request) {
		HttpSession current = request.getSession(false);
		String currentId = current == null ? null : current.getId();
		sessionRepository.findByPrincipalName(String.valueOf(userId)).keySet().stream()
				.filter(sessionId -> !sessionId.equals(currentId))
				.forEach(sessionRepository::deleteById);
	}

	/** 이 세션이 아직 살아 있는지. 지워졌거나 만료됐으면 false다. */
	public boolean isAlive(String sessionId) {
		return sessionRepository.findById(sessionId) != null;
	}

	/** 지금 요청의 세션만 지운다. 다른 기기의 세션은 그대로다. 쿠키는 Spring Session이 지운다. */
	public void end(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
	}

}
