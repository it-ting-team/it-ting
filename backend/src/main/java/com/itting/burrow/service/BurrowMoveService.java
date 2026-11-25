package com.itting.burrow.service;

import java.sql.SQLException;
import java.util.function.Supplier;

import com.itting.auth.domain.AuthErrorCode;
import com.itting.auth.domain.NextStep;
import com.itting.burrow.domain.Burrow;
import com.itting.burrow.domain.BurrowEntrance;
import com.itting.burrow.dto.BurrowMoveRequest;
import com.itting.burrow.dto.BurrowMoveResponse;
import com.itting.burrow.dto.MyBurrowResponse.EntranceResponse;
import com.itting.burrow.repository.BurrowEntranceRepository;
import com.itting.burrow.repository.BurrowRepository;
import com.itting.campus.service.CampusService;
import com.itting.global.error.BusinessException;
import com.itting.global.error.CommonErrorCode;
import com.itting.term.service.TermService;
import com.itting.user.domain.User;
import com.itting.user.domain.UserProfile;
import com.itting.user.repository.UserProfileRepository;
import com.itting.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.function.SingletonSupplier;

@Service
public class BurrowMoveService {

	private static final int MYSQL_DUPLICATE_ENTRY = 1062;
	private static final String FIELD_SLOT_CONSTRAINT = "uk_burrow_entrance_field_slot";

	private final TransactionTemplate transaction;
	private final UserRepository userRepository;
	private final UserProfileRepository profileRepository;
	private final BurrowRepository burrowRepository;
	private final BurrowEntranceRepository entranceRepository;
	private final TermService termService;
	private final CampusService campusService;
	private final BurrowService burrowService;

	public BurrowMoveService(PlatformTransactionManager transactionManager, UserRepository userRepository,
			UserProfileRepository profileRepository, BurrowRepository burrowRepository,
			BurrowEntranceRepository entranceRepository, TermService termService, CampusService campusService,
			BurrowService burrowService) {
		this.transaction = new TransactionTemplate(transactionManager);
		this.userRepository = userRepository;
		this.profileRepository = profileRepository;
		this.burrowRepository = burrowRepository;
		this.entranceRepository = entranceRepository;
		this.termService = termService;
		this.campusService = campusService;
		this.burrowService = burrowService;
	}

	/** 이사할 때에만 본문을 읽는다. 자리 충돌로 롤백했다면 읽었던 값으로 한 번 다시 시도한다. */
	public BurrowMoveResponse move(Long userId, Supplier<BurrowMoveRequest> readRequest) {
		Supplier<BurrowMoveRequest> request = SingletonSupplier.of(readRequest);
		try {
			return transaction.execute(status -> moveInTransaction(userId, request));
		}
		catch (DataIntegrityViolationException e) {
			if (!(e.getMostSpecificCause() instanceof SQLException cause)
					|| cause.getErrorCode() != MYSQL_DUPLICATE_ENTRY
					|| !cause.getMessage().contains(FIELD_SLOT_CONSTRAINT)) {
				throw e;
			}
			return transaction.execute(status -> moveInTransaction(userId, request));
		}
	}

	private BurrowMoveResponse moveInTransaction(Long userId, Supplier<BurrowMoveRequest> request) {
		User user = userRepository.findByIdForUpdate(userId).filter(User::isActive)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
		if (!user.isOnboarded()) {
			throw new BusinessException(AuthErrorCode.ONBOARDING_INCOMPLETE);
		}
		// 내 정보 수정과 두더지 저장도 이 행을 잠근다. 입구를 읽기 전에 잠금을 잡아 이전 값을 보지 않는다.
		UserProfile profile = profileRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new IllegalStateException("프로필이 없는 계정: " + userId));
		Long termId = termService.findCurrentTermId()
				.orElseThrow(() -> new IllegalStateException("열린 학기가 없어 땅굴 입구를 옮길 수 없습니다."));
		Burrow burrow = burrowRepository.findByUserId(userId)
				.orElseThrow(() -> new IllegalStateException("온보딩을 마쳤는데 땅굴이 없는 계정: " + userId));
		BurrowEntrance entrance = entranceRepository.findByBurrowId(burrow.getId())
				.orElseThrow(() -> new IllegalStateException("입구가 없는 땅굴: " + burrow.getId()));
		if (!termId.equals(entrance.getTermId())) {
			Long campusFloorId = request.get().campusFloorId();
			campusService.getAvailableFloor(campusFloorId);
			// 목적지 층의 배타 잠금을 먼저 잡아야 프로필 갱신의 외래 키 검사와 교착이 생기지 않는다.
			entrance = burrowService.moveEntrance(burrow.getId(), termId, campusFloorId);
			profile.moveToCampusFloor(campusFloorId);
		}
		return new BurrowMoveResponse(userId, NextStep.HOME,
				new EntranceResponse(entrance.getTermId(), entrance.getCampusFloorId(), entrance.getSlot()));
	}

}
