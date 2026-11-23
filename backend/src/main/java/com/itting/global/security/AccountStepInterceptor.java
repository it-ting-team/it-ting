package com.itting.global.security;

import java.lang.annotation.Annotation;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.domain.NextStep;
import com.itting.auth.service.NextStepService;
import com.itting.global.error.BusinessException;
import com.itting.user.domain.User;
import com.itting.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 온보딩이나 새 학기 선택을 마치지 않은 계정이 부를 수 없는 API를 막는다({@code docs/api/02-auth.md}의
 * "온보딩을 마치지 않은 계정", "새 학기를 고르지 않은 계정").
 *
 * <ul>
 * <li>{@link AllowDuringOnboarding}을 붙이지 않은 API는 온보딩을 마치지 않은 계정에게 {@code 403 ONBOARDING_INCOMPLETE}다.</li>
 * <li>{@link AllowBeforeNewTerm}을 붙이지 않은 API는 새 학기를 고르지 않은 계정에게 {@code 403 NEW_TERM_INCOMPLETE}다.</li>
 * </ul>
 *
 * <p>표시를 잊은 새 API는 막히는 쪽이다. 로그인하지 않은 요청은 보지 않는다(로그인 검사는 {@link SecurityConfig}가 한다).
 * 계정이 없거나 탈퇴했으면 막지 않고 넘긴다. 세션만 남은 경우라 각 API가 다룬다.
 */
@Component
public class AccountStepInterceptor implements HandlerInterceptor {

	private final UserRepository userRepository;

	private final NextStepService nextStepService;

	public AccountStepInterceptor(UserRepository userRepository, NextStepService nextStepService) {
		this.userRepository = userRepository;
		this.nextStepService = nextStepService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}
		boolean duringOnboarding = has(method, AllowDuringOnboarding.class);
		boolean beforeNewTerm = has(method, AllowBeforeNewTerm.class);
		if (duringOnboarding && beforeNewTerm) {
			return true;
		}
		Long userId = loginUserId();
		if (userId == null) {
			return true;
		}
		User user = userRepository.findById(userId).filter(User::isActive).orElse(null);
		if (user == null) {
			return true;
		}
		NextStep step = nextStepService.of(user);
		if (step == NextStep.ONBOARDING && !duringOnboarding) {
			throw new BusinessException(AuthErrorCode.ONBOARDING_INCOMPLETE);
		}
		if (step == NextStep.NEW_TERM && !beforeNewTerm) {
			throw new BusinessException(AuthErrorCode.NEW_TERM_INCOMPLETE);
		}
		return true;
	}

	private static boolean has(HandlerMethod method, Class<? extends Annotation> annotation) {
		return method.hasMethodAnnotation(annotation)
				|| AnnotatedElementUtils.hasAnnotation(method.getBeanType(), annotation);
	}

	/** 로그인했으면 계정 id. 세션의 로그인 정보는 {@code SessionService}가 계정 id를 principal로 넣는다. */
	private static Long loginUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
			return null;
		}
		return userId;
	}

}
