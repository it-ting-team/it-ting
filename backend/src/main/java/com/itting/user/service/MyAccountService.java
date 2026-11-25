package com.itting.user.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.domain.GoogleOAuthPurpose;
import com.itting.auth.domain.GoogleVerification;
import com.itting.auth.service.GoogleVerificationService;
import com.itting.auth.service.SessionService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.user.domain.User;
import com.itting.user.domain.UserErrorCode;
import com.itting.user.dto.MyAccountResponse;
import com.itting.user.dto.PasswordChangeRequest;
import com.itting.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 설정의 계정 정보, 비밀번호 변경, Google 연동 · 해제({@code /api/v1/me/account}, {@code /api/v1/me/password},
 * {@code /api/v1/me/google-link}).
 */
@Service
public class MyAccountService {

	/** BCrypt가 보는 비밀번호의 최대 길이. 넘는 값은 비교하지 않고 틀린 것으로 본다(로그인과 같다). */
	private static final int BCRYPT_MAX_BYTES = 72;

	private final UserRepository userRepository;

	private final UserService userService;

	private final SessionService sessionService;

	private final GoogleVerificationService googleVerificationService;

	private final PasswordEncoder passwordEncoder;

	public MyAccountService(UserRepository userRepository, UserService userService, SessionService sessionService,
			GoogleVerificationService googleVerificationService, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.userService = userService;
		this.sessionService = sessionService;
		this.googleVerificationService = googleVerificationService;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * 가입 방법과 Google 연동 상태를 준다. 있는지만 주고 비밀번호나 Google 식별자는 주지 않는다.
	 *
	 * @throws BusinessException 계정이 없거나 탈퇴했는데 세션만 남았으면 세션을 지우고 {@code UNAUTHENTICATED}
	 */
	public MyAccountResponse getAccount(Long userId, HttpServletRequest request) {
		return toResponse(findActiveUser(userId, request));
	}

	/**
	 * 이 세션에서 확인받은 Google 계정을 내 계정에 연결한다. 확인 기록은 쓰고 나면 지운다.
	 *
	 * @return 연결한 뒤의 계정 정보
	 * @throws BusinessException 확인 기록(LINK)이 없거나 10분이 지났으면 {@code GOOGLE_VERIFICATION_REQUIRED}(이미
	 *         연동된 계정은 바꾸지 않고 성공), 이미 다른 Google 계정이 연결돼 있으면 {@code GOOGLE_ALREADY_LINKED},
	 *         다른 계정이 쓰고 있으면 {@code GOOGLE_ACCOUNT_DUPLICATED}. 계정이 없거나 탈퇴했는데 세션만 남았으면 세션을
	 *         지우고 {@code UNAUTHENTICATED}
	 */
	public MyAccountResponse linkGoogle(Long userId, HttpServletRequest request) {
		String sessionId = sessionId(request);
		Optional<GoogleVerification> verification = googleVerificationService.find(sessionId, GoogleOAuthPurpose.LINK);
		if (verification.isEmpty()) {
			User user = findActiveUser(userId, request);
			if (user.getGoogleSub() != null) {
				// 이미 연동된 계정은 바꾸지 않고 성공으로 답한다(같은 요청을 다시 불러도 같다).
				return toResponse(user);
			}
			throw new BusinessException(AuthErrorCode.GOOGLE_VERIFICATION_REQUIRED);
		}
		try {
			User linked = userService.linkGoogle(userId, verification.get().googleSub(), verification.get().googleEmail())
					.orElseThrow(() -> {
						sessionService.end(request);
						return new BusinessException(CommonErrorCode.UNAUTHENTICATED);
					});
			return toResponse(linked);
		}
		finally {
			// 연결했든 겹쳐서 못 했든 이 확인 기록으로는 더 할 일이 없다.
			googleVerificationService.delete(sessionId);
		}
	}

	/**
	 * Google 연동을 해제한다. 연동돼 있지 않으면 바꾸지 않고 성공한다. 로그인 세션은 지우지 않는다.
	 *
	 * @return 해제한 뒤의 계정 정보
	 * @throws BusinessException 비밀번호가 없는 계정이면 {@code GOOGLE_UNLINK_NOT_ALLOWED}. 계정이 없거나 탈퇴했는데
	 *         세션만 남았으면 세션을 지우고 {@code UNAUTHENTICATED}
	 */
	public MyAccountResponse unlinkGoogle(Long userId, HttpServletRequest request) {
		return toResponse(userService.unlinkGoogle(userId).orElseThrow(() -> {
			sessionService.end(request);
			return new BusinessException(CommonErrorCode.UNAUTHENTICATED);
		}));
	}

	/**
	 * 비밀번호를 바꾸고 이 계정의 다른 기기를 로그아웃시킨다. 지금 요청의 세션은 남긴다.
	 *
	 * @throws BusinessException 비밀번호가 없는 계정이면 {@code PASSWORD_NOT_SET}, 지금 비밀번호가 틀리면
	 *         {@code CURRENT_PASSWORD_MISMATCH}, 새 비밀번호가 같으면 {@code PASSWORD_UNCHANGED}.
	 *         계정이 없거나 탈퇴했는데 세션만 남았으면 세션을 지우고 {@code UNAUTHENTICATED}
	 */
	public void changePassword(Long userId, PasswordChangeRequest body, HttpServletRequest request) {
		User user = findActiveUser(userId, request);
		String passwordHash = user.getPasswordHash();
		if (passwordHash == null) {
			throw new BusinessException(UserErrorCode.PASSWORD_NOT_SET);
		}
		if (!matches(body.currentPassword(), passwordHash)) {
			throw new BusinessException(UserErrorCode.CURRENT_PASSWORD_MISMATCH);
		}
		if (matches(body.newPassword(), passwordHash)) {
			throw new BusinessException(UserErrorCode.PASSWORD_UNCHANGED);
		}
		if (!userService.changePassword(userId, passwordEncoder.encode(body.newPassword()))) {
			// 확인한 뒤 탈퇴했다. 로그인하지 않은 것으로 답한다.
			sessionService.end(request);
			throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
		}
		sessionService.endOthers(userId, request);
	}

	/** 가입 방법과 Google 연동 상태. 있는지만 주고 비밀번호나 Google 식별자는 주지 않는다. */
	private static MyAccountResponse toResponse(User user) {
		boolean hasPassword = user.getPasswordHash() != null;
		boolean isGoogleLinked = user.getGoogleSub() != null;
		return new MyAccountResponse(hasPassword, isGoogleLinked, isGoogleLinked ? user.getGoogleEmail() : null,
				isGoogleLinked && hasPassword);
	}

	private static String sessionId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return session == null ? null : session.getId();
	}

	private User findActiveUser(Long userId, HttpServletRequest request) {
		return userRepository.findById(userId).filter(User::isActive).orElseThrow(() -> {
			// 계정이 없거나 탈퇴했는데 세션만 남았다. 세션을 지우고 로그인하지 않은 것으로 답한다.
			sessionService.end(request);
			return new BusinessException(CommonErrorCode.UNAUTHENTICATED);
		});
	}

	private boolean matches(String password, String passwordHash) {
		return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
				&& passwordEncoder.matches(password, passwordHash);
	}

}
