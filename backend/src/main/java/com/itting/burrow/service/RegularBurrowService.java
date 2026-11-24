package com.itting.burrow.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.itting.burrow.domain.Burrow;
import com.itting.burrow.domain.BurrowErrorCode;
import com.itting.burrow.dto.RegularBurrowListResponse;
import com.itting.burrow.dto.RegularBurrowListResponse.RegularBurrowResponse;
import com.itting.burrow.repository.RegularBurrowRepository;
import com.itting.burrow.repository.RegularBurrowRepository.SavedBurrow;
import com.itting.global.error.BusinessException;
import com.itting.user.dto.UserDisplayResponse;
import com.itting.user.service.UserDisplayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RegularBurrowService {

	private final RegularBurrowRepository repository;
	private final BurrowService burrowService;
	private final UserDisplayService userDisplayService;
	private final Clock clock;

	public RegularBurrowService(RegularBurrowRepository repository, BurrowService burrowService,
			UserDisplayService userDisplayService, Clock clock) {
		this.repository = repository;
		this.burrowService = burrowService;
		this.userDisplayService = userDisplayService;
		this.clock = clock;
	}

	public RegularBurrowListResponse list(Long userId) {
		List<SavedBurrow> saved = repository.findSavedBurrows(userId);
		Map<Long, UserDisplayResponse> owners = userDisplayService.getAll(saved.stream()
				.map(SavedBurrow::getOwnerId).toList());
		return new RegularBurrowListResponse(saved.stream()
				.map(row -> new RegularBurrowResponse(row.getBurrowId(), owners.get(row.getOwnerId()), row.getCreatedAt()))
				.toList());
	}

	@Transactional
	public void add(Long userId, Long burrowId) {
		Burrow burrow = burrowService.lockForInteraction(burrowId, userId);
		if (burrow.getUserId().equals(userId)) {
			throw new BusinessException(BurrowErrorCode.OWN_BURROW_NOT_ALLOWED);
		}
		repository.insertIfAbsent(userId, burrowId, clock.instant());
	}

	@Transactional
	public void remove(Long userId, Long burrowId) {
		burrowService.lockForInteraction(burrowId, userId);
		repository.remove(userId, burrowId);
	}

}
