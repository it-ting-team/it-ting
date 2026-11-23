package com.itting.user.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.itting.user.domain.UserStatus;
import com.itting.user.dto.MoleResponse;
import com.itting.user.dto.UserSummaryResponse;
import com.itting.user.repository.UserProfileRepository;
import com.itting.user.repository.UserSummaryRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 표시({@code docs/api/00-common.md}의 "사용자 표시")를 만든다. 글의 작성자, 방명록을 쓴 사람, 대화 상대처럼
 * 사람을 보여 주는 API가 함께 쓴다. 캠퍼스와 층은 프로필에 저장된 지금의 값이다.
 */
@Service
@Transactional(readOnly = true)
public class UserSummaryService {

	private final UserProfileRepository userProfileRepository;

	public UserSummaryService(UserProfileRepository userProfileRepository) {
		this.userProfileRepository = userProfileRepository;
	}

	/**
	 * 여러 사람의 사용자 표시를 한 번의 조회로 만든다.
	 *
	 * @return 계정 id → 사용자 표시. 없는 계정 id는 담지 않는다
	 */
	public Map<Long, UserSummaryResponse> getSummaries(Collection<Long> userIds) {
		Map<Long, UserSummaryResponse> summaries = new HashMap<>();
		if (userIds.isEmpty()) {
			return summaries;
		}
		for (UserSummaryRow row : userProfileRepository.findSummaryRows(userIds)) {
			summaries.put(row.userId(), toSummary(row));
		}
		return summaries;
	}

	/**
	 * 한 사람의 사용자 표시.
	 *
	 * @throws IllegalStateException 없는 계정. 부른 쪽이 계정이 있는 것을 확인한 뒤 부른다
	 */
	public UserSummaryResponse getSummary(Long userId) {
		UserSummaryResponse summary = getSummaries(List.of(userId)).get(userId);
		if (summary == null) {
			throw new IllegalStateException("없는 계정: " + userId);
		}
		return summary;
	}

	/** 탈퇴하면 프로필을 지우므로, 탈퇴한 사용자는 {@code id}와 {@code isWithdrawn}만 값이 있다. */
	private static UserSummaryResponse toSummary(UserSummaryRow row) {
		if (row.status() == UserStatus.WITHDRAWN) {
			return UserSummaryResponse.withdrawn(row.userId());
		}
		return new UserSummaryResponse(row.userId(), row.nickname(), row.profileImageUrl(),
				new MoleResponse(row.moleHead(), row.moleFace(), row.moleNeck(), row.moleHand()), row.campusName(),
				row.generation() == null ? null : row.generation().intValue(), row.floor(), false);
	}

}
