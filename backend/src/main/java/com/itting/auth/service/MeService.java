package com.itting.auth.service;

import java.util.Optional;

import com.itting.auth.domain.NextStep;
import com.itting.auth.dto.MeResponse;
import com.itting.campus.domain.Campus;
import com.itting.campus.domain.CampusFloor;
import com.itting.campus.repository.CampusFloorRepository;
import com.itting.campus.repository.CampusRepository;
import com.itting.user.domain.User;
import com.itting.user.domain.UserProfile;
import com.itting.user.repository.UserProfileRepository;
import com.itting.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 계정의 상태와 소속을 준다({@code GET /api/v1/me}).
 */
@Service
public class MeService {

	private final UserRepository userRepository;

	private final UserProfileRepository userProfileRepository;

	private final CampusFloorRepository campusFloorRepository;

	private final CampusRepository campusRepository;

	public MeService(UserRepository userRepository, UserProfileRepository userProfileRepository,
			CampusFloorRepository campusFloorRepository, CampusRepository campusRepository) {
		this.userRepository = userRepository;
		this.userProfileRepository = userProfileRepository;
		this.campusFloorRepository = campusFloorRepository;
		this.campusRepository = campusRepository;
	}

	/**
	 * @return 계정이 없거나 탈퇴했으면 빈 값. 세션만 남은 경우이니 부른 쪽이 세션을 지운다
	 */
	@Transactional(readOnly = true)
	public Optional<MeResponse> findMe(Long userId) {
		return userRepository.findById(userId).filter(User::isActive).map(this::toResponse);
	}

	/** 계정을 만들 때 프로필을 함께 만들므로 프로필과 캠퍼스-층은 항상 있다. 없으면 데이터가 어긋난 것이다. */
	private MeResponse toResponse(User user) {
		UserProfile profile = userProfileRepository.findById(user.getId())
				.orElseThrow(() -> new IllegalStateException("프로필이 없는 계정: " + user.getId()));
		CampusFloor floor = campusFloorRepository.findById(profile.getCampusFloorId())
				.orElseThrow(() -> new IllegalStateException("없는 캠퍼스-층: " + profile.getCampusFloorId()));
		Campus campus = campusRepository.findById(floor.getCampusId())
				.orElseThrow(() -> new IllegalStateException("없는 캠퍼스: " + floor.getCampusId()));
		return new MeResponse(user.getId(), NextStep.of(user), campus.getName(), profile.getGeneration(),
				floor.getFloor(), floor.getId());
	}

}
