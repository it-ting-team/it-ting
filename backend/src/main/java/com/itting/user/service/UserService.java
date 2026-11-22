package com.itting.user.service;

import java.time.Instant;

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
