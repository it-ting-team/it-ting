package com.itting.user.service;

import java.time.Instant;
import java.util.Optional;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.global.error.BusinessException;
import com.itting.user.domain.User;
import com.itting.user.domain.UserProfile;
import com.itting.user.repository.UserProfileRepository;
import com.itting.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정과 프로필을 만든다. 회원가입({@code POST /api/v1/users})이 부른다.
 */
@Service
public class UserService {

	private final UserRepository userRepository;

	private final UserProfileRepository userProfileRepository;

	public UserService(UserRepository userRepository, UserProfileRepository userProfileRepository) {
		this.userRepository = userRepository;
		this.userProfileRepository = userProfileRepository;
	}

	/**
	 * 계정과 프로필을 한 트랜잭션으로 만든다. 그 사이 다른 사람이 같은 값으로 가입했으면 모두 되돌린다.
	 *
	 * @return 만든 계정의 id
	 * @throws BusinessException 이메일이 겹치면 {@code EMAIL_DUPLICATED}, Mattermost ID가 겹치면
	 *         {@code MATTERMOST_ID_DUPLICATED}
	 */
	@Transactional
	public Long createEmailUser(String email, String passwordHash, String mattermostId, int generation,
			Long campusFloorId, Instant now) {
		try {
			User user = userRepository.saveAndFlush(User.createByEmail(email, passwordHash, mattermostId, now));
			userProfileRepository.saveAndFlush(UserProfile.create(user.getId(), generation, campusFloorId));
			return user.getId();
		}
		catch (DataIntegrityViolationException e) {
			throw toDuplicated(e);
		}
	}

	/**
	 * Google 가입 계정과 프로필을 한 트랜잭션으로 만든다. {@code email}과 {@code password_hash}는 비워 둔다.
	 * 그 사이 다른 사람이 같은 값으로 가입했으면 모두 되돌린다.
	 *
	 * @return 만든 계정의 id
	 * @throws BusinessException Google 계정이 겹치면 {@code GOOGLE_ACCOUNT_DUPLICATED}, Mattermost ID가 겹치면
	 *         {@code MATTERMOST_ID_DUPLICATED}
	 */
	@Transactional
	public Long createGoogleUser(String googleSub, String googleEmail, String mattermostId, int generation,
			Long campusFloorId, Instant now) {
		try {
			User user = userRepository.saveAndFlush(User.createByGoogle(googleSub, googleEmail, mattermostId, now));
			userProfileRepository.saveAndFlush(UserProfile.create(user.getId(), generation, campusFloorId));
			return user.getId();
		}
		catch (DataIntegrityViolationException e) {
			throw toDuplicated(e);
		}
	}

	/**
	 * 확인받은 Google 계정을 계정에 연결한다. 같은 계정의 연동 · 해제가 겹치지 않게 계정 행을 잠근다.
	 * 이미 같은 Google 계정이 연결돼 있으면 바꾸지 않고 끝낸다.
	 *
	 * @return 연결한 뒤의 계정. 계정이 없거나 탈퇴했으면 empty
	 * @throws BusinessException 이미 다른 Google 계정이 연결돼 있으면 {@code GOOGLE_ALREADY_LINKED}, 다른 계정이
	 *         쓰고 있으면 {@code GOOGLE_ACCOUNT_DUPLICATED}
	 */
	@Transactional
	public Optional<User> linkGoogle(Long userId, String googleSub, String googleEmail) {
		User user = userRepository.findByIdForUpdate(userId).filter(User::isActive).orElse(null);
		if (user == null) {
			return Optional.empty();
		}
		if (googleSub.equals(user.getGoogleSub())) {
			return Optional.of(user);
		}
		if (user.getGoogleSub() != null) {
			throw new BusinessException(AuthErrorCode.GOOGLE_ALREADY_LINKED);
		}
		if (userRepository.existsByGoogleSub(googleSub)) {
			throw new BusinessException(AuthErrorCode.GOOGLE_ACCOUNT_DUPLICATED);
		}
		try {
			user.linkGoogle(googleSub, googleEmail);
			userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException e) {
			// 위에서 본 뒤 그 사이 다른 계정이 같은 Google 계정을 썼다.
			throw toDuplicated(e);
		}
		return Optional.of(user);
	}

	/**
	 * Google 연동을 해제한다. 연동돼 있지 않으면 바꾸지 않는다.
	 *
	 * @return 해제한 뒤의 계정. 계정이 없거나 탈퇴했으면 empty
	 * @throws BusinessException 비밀번호가 없는 계정이면 {@code GOOGLE_UNLINK_NOT_ALLOWED}
	 */
	@Transactional
	public Optional<User> unlinkGoogle(Long userId) {
		User user = userRepository.findByIdForUpdate(userId).filter(User::isActive).orElse(null);
		if (user == null) {
			return Optional.empty();
		}
		if (user.getGoogleSub() == null) {
			return Optional.of(user);
		}
		if (user.getPasswordHash() == null) {
			throw new BusinessException(AuthErrorCode.GOOGLE_UNLINK_NOT_ALLOWED);
		}
		user.unlinkGoogle();
		userRepository.saveAndFlush(user);
		return Optional.of(user);
	}

	/**
	 * 비밀번호를 바꾼다.
	 *
	 * @return 바꿀 계정이 없거나 탈퇴했으면 false
	 */
	@Transactional
	public boolean changePassword(Long userId, String passwordHash) {
		return userRepository.findById(userId)
				.filter(User::isActive)
				.map(user -> {
					user.changePassword(passwordHash);
					return true;
				})
				.orElse(false);
	}

	/** UNIQUE 제약의 이름(V3 마이그레이션)으로 어느 값이 겹쳤는지 가린다. */
	private static RuntimeException toDuplicated(DataIntegrityViolationException e) {
		String message = String.valueOf(e.getMostSpecificCause().getMessage());
		if (message.contains("uk_user_email")) {
			return new BusinessException(AuthErrorCode.EMAIL_DUPLICATED);
		}
		if (message.contains("uk_user_mattermost_id")) {
			return new BusinessException(AuthErrorCode.MATTERMOST_ID_DUPLICATED);
		}
		if (message.contains("uk_user_google_sub")) {
			return new BusinessException(AuthErrorCode.GOOGLE_ACCOUNT_DUPLICATED);
		}
		return e;
	}

}
