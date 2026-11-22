package com.itting.interest.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.itting.interest.domain.Interest;
import com.itting.interest.domain.InterestCategory;
import com.itting.interest.dto.InterestCategoryListResponse;
import com.itting.interest.dto.InterestCategoryListResponse.InterestCategoryResponse;
import com.itting.interest.dto.InterestCategoryListResponse.InterestResponse;
import com.itting.interest.repository.InterestCategoryRepository;
import com.itting.interest.repository.InterestRepository;
import com.itting.interest.repository.InterestUserCount;
import com.itting.interest.repository.UserInterestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InterestService {

	private final InterestCategoryRepository interestCategoryRepository;

	private final InterestRepository interestRepository;

	private final UserInterestRepository userInterestRepository;

	public InterestService(InterestCategoryRepository interestCategoryRepository,
			InterestRepository interestRepository, UserInterestRepository userInterestRepository) {
		this.interestCategoryRepository = interestCategoryRepository;
		this.interestRepository = interestRepository;
		this.userInterestRepository = userInterestRepository;
	}

	/**
	 * 지우지 않은 대분류와 세부 관심사를 모두 id 순으로 준다. 세부 관심사마다 고른 사람 수를 함께 준다.
	 * 목록이 작아(대분류 12개, 세부 90개) 세 번의 조회로 모두 읽는다.
	 */
	public InterestCategoryListResponse getInterestCategories() {
		Map<Long, Long> userCounts = userInterestRepository.countUsersByInterest().stream()
				.collect(Collectors.toMap(InterestUserCount::interestId, InterestUserCount::userCount));
		Map<Long, List<InterestResponse>> interestsByCategoryId = interestRepository
				.findAllByDeletedAtIsNullOrderByIdAsc()
				.stream()
				.collect(Collectors.groupingBy(Interest::getInterestCategoryId,
						Collectors.mapping(interest -> new InterestResponse(interest.getId(), interest.getName(),
								userCounts.getOrDefault(interest.getId(), 0L)), Collectors.toList())));

		List<InterestCategoryResponse> items = interestCategoryRepository.findAllByDeletedAtIsNullOrderByIdAsc()
				.stream()
				.map((InterestCategory category) -> new InterestCategoryResponse(category.getId(), category.getName(),
						interestsByCategoryId.getOrDefault(category.getId(), List.of())))
				.toList();
		return new InterestCategoryListResponse(items);
	}

}
