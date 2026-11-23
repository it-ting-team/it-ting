package com.itting.interest.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.itting.interest.dto.InterestTagResponse;
import com.itting.interest.repository.UserInterestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관심사 태그({@code docs/api/00-common.md}의 "관심사 태그")를 만든다. 내 땅굴, 프로필 팝업처럼 사람의 관심사를 보여 주는 API가 쓴다.
 */
@Service
@Transactional(readOnly = true)
public class InterestTagService {

	private final UserInterestRepository userInterestRepository;

	public InterestTagService(UserInterestRepository userInterestRepository) {
		this.userInterestRepository = userInterestRepository;
	}

	/** 내 관심사. 고른 순서이고 {@code isShared}는 모두 false다. */
	public List<InterestTagResponse> getMyTags(Long userId) {
		return userInterestRepository.findTagsByUserId(userId);
	}

	/**
	 * 다른 사람의 관심사를 고른 순서로 준다. {@code isShared}는 보는 사람의 관심사에 같은 것이 있는지다.
	 * 자기 자신을 보면 모두 false다.
	 *
	 * @param ownerId 관심사를 보여 줄 사람
	 * @param viewerId 보는 사람(요청한 사람)
	 */
	public List<InterestTagResponse> getTags(Long ownerId, Long viewerId) {
		List<InterestTagResponse> tags = userInterestRepository.findTagsByUserId(ownerId);
		if (ownerId.equals(viewerId)) {
			return tags;
		}
		Set<Long> viewerInterestIds = new HashSet<>(userInterestRepository.findInterestIdsByUserId(viewerId));
		return tags.stream()
				.map(tag -> new InterestTagResponse(tag.id(), tag.name(), tag.interestCategoryId(),
						viewerInterestIds.contains(tag.id())))
				.toList();
	}

}
