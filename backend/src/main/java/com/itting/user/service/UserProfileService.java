package com.itting.user.service;

import com.itting.global.error.BusinessException;
import com.itting.interest.service.InterestTagService;
import com.itting.user.domain.UserErrorCode;
import com.itting.user.dto.UserProfileResponse;
import com.itting.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 팝업({@code GET /api/v1/users/{userId}/profile})의 값을 만든다.
 */
@Service
@Transactional(readOnly = true)
public class UserProfileService {

	private final UserRepository userRepository;

	private final UserDisplayService userDisplayService;

	private final InterestTagService interestTagService;

	public UserProfileService(UserRepository userRepository, UserDisplayService userDisplayService,
			InterestTagService interestTagService) {
		this.userRepository = userRepository;
		this.userDisplayService = userDisplayService;
		this.interestTagService = interestTagService;
	}

	/**
	 * 가입을 모두 마친 사람(활동 중이고 온보딩을 마친 계정)만 준다. 정지된 사람도 준다. 명세에서 정지는 로그인을 막는 것뿐이다.
	 *
	 * @param viewerId 요청한 사람. 관심사의 {@code isShared}를 이 사람 기준으로 계산한다
	 * @throws BusinessException 없거나, 탈퇴했거나, 온보딩을 마치지 않은 사람이면 {@code USER_NOT_FOUND}
	 */
	public UserProfileResponse getProfile(Long userId, Long viewerId) {
		userRepository.findById(userId)
				.filter(user -> user.isActive() && user.isOnboarded())
				.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
		return new UserProfileResponse(userDisplayService.get(userId),
				interestTagService.getTags(userId, viewerId));
	}

}
