package com.itting.user.service;

import java.nio.charset.StandardCharsets;

import com.itting.auth.service.SessionService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.user.domain.User;
import com.itting.user.domain.UserErrorCode;
import com.itting.user.dto.MyAccountResponse;
import com.itting.user.dto.PasswordChangeRequest;
import com.itting.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 설정의 계정 정보와 비밀번호 변경({@code /api/v1/me/account}, {@code /api/v1/me/password}).
 */
@Service
public class MyAccountService {

	/** BCrypt가 보는 비밀번호의 최대 길이. 넘는 값은 비교하지 않고 틀린 것으로 본다(로그인과 같다). */
	private static final int BCRYPT_MAX_BYTES = 72;

	private final UserRepository userRepository;

	private final UserService userService;

	private final SessionService sessionService;

	private final PasswordEncoder passwordEncoder;

	public MyAccountService(UserRepository userRepository, UserService userService, SessionService sessionService,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.userService = userService;
		this.sessionService = sessionService;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * 가입 방법과 Google 연동 상태를 준다. 있는지만 주고 비밀번호나 Google 식별자는 주지 않는다.
	 *
	 * @throws BusinessException 계정이 없거나 탈퇴했는데 세션만 남았으면 세션을 지우고 {@code UNAUTHENTICATED}
	 */
	public MyAccountResponse getAccount(Long userId, HttpServletRequest request) {
		User user = findActiveUser(userId, request);
		boolean hasPassword = user.getPasswordHash() != null;
		boolean isGoogleLinked = user.getGoogleSub() != null;
		return new MyAccountResponse(hasPassword, isGoogleLinked, isGoogleLinked ? user.getGoogleEmail() : null,
				isGoogleLinked && hasPassword);
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
