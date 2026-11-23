package com.itting.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.itting.global.error.BusinessException;
import com.itting.user.domain.UserErrorCode;
import com.itting.user.dto.NicknameCheckResponse;
import com.itting.user.repository.NicknameHoldRepository;
import com.itting.user.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임을 쓸 수 있는지 확인하고 맡아 둔다({@code docs/api/03-onboarding.md}의 "닉네임 맡아 두기").
 * 닉네임을 저장하는 API(온보딩 확정, 내 정보 수정)도 저장 전후에 이것을 쓴다.
 */
@Service
public class NicknameService {

	/** 확인한 닉네임을 맡아 두는 시간. */
	public static final Duration HOLD_TTL = Duration.ofMinutes(5);

	private final UserProfileRepository userProfileRepository;

	private final NicknameHoldRepository nicknameHoldRepository;

	private final Clock clock;

	public NicknameService(UserProfileRepository userProfileRepository, NicknameHoldRepository nicknameHoldRepository,
			Clock clock) {
		this.userProfileRepository = userProfileRepository;
		this.nicknameHoldRepository = nicknameHoldRepository;
		this.clock = clock;
	}

	/**
	 * 다른 사람이 쓰거나 맡아 두지 않았으면 이 계정 몫으로 5분 동안 맡아 둔다. 내 지금 닉네임은 쓸 수 있는 것으로 본다.
	 *
	 * @throws BusinessException 다른 사람이 쓰거나 맡아 두었으면 {@code NICKNAME_DUPLICATED}
	 */
	@Transactional(readOnly = true)
	public NicknameCheckResponse check(Long userId, String nickname) {
		Instant now = clock.instant();
		if (userProfileRepository.existsByNicknameAndUserIdNot(nickname, userId)
				|| !nicknameHoldRepository.hold(nickname, userId, HOLD_TTL)) {
			throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
		}
		return new NicknameCheckResponse(now.plus(HOLD_TTL));
	}

	/**
	 * 닉네임을 저장하기 전에 부른다. 다른 계정이 맡아 둔 시간이 남아 있으면 막는다. 이 계정이 맡았거나 아무도 맡지 않았으면
	 * 넘어간다. 다른 사람이 이미 쓰고 있는지는 부른 쪽이 저장할 때 DB의 UNIQUE로 확인한다.
	 *
	 * @throws BusinessException 다른 계정이 맡아 두었으면 {@code NICKNAME_DUPLICATED}
	 */
	public void assertNotHeldByOther(Long userId, String nickname) {
		if (nicknameHoldRepository.isHeldByOther(nickname, userId)) {
			throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
		}
	}

	/** 닉네임을 저장한 뒤 부른다. 이 계정이 맡아 둔 닉네임을 푼다. */
	public void release(Long userId) {
		nicknameHoldRepository.release(userId);
	}

}
