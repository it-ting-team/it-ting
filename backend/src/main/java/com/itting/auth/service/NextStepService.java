package com.itting.auth.service;

import java.util.Optional;

import com.itting.auth.domain.NextStep;
import com.itting.burrow.repository.BurrowEntranceRepository;
import com.itting.term.service.TermService;
import com.itting.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정이 다음에 갈 화면을 계산한다({@code docs/api/02-auth.md}의 "다음에 갈 화면"). 저장하지 않는다.
 * 로그인, 내 계정 조회, 온보딩 · 새 학기를 마치지 않은 계정을 막는 장치가 함께 쓴다.
 */
@Service
public class NextStepService {

	private final TermService termService;

	private final BurrowEntranceRepository burrowEntranceRepository;

	public NextStepService(TermService termService, BurrowEntranceRepository burrowEntranceRepository) {
		this.termService = termService;
		this.burrowEntranceRepository = burrowEntranceRepository;
	}

	/**
	 * <ul>
	 * <li>온보딩을 마치지 않았으면 {@link NextStep#ONBOARDING}</li>
	 * <li>땅굴 입구가 이번 학기 들판에 있지 않으면 {@link NextStep#NEW_TERM}</li>
	 * <li>그 밖에는 {@link NextStep#HOME}. 열린 학기가 하나도 없거나 땅굴이 없을 때(데이터가 어긋난 경우)도 여기다.
	 * 고를 학기나 옮길 땅굴이 없어 새 학기 안내를 띄워도 끝낼 수 없기 때문이다.</li>
	 * </ul>
	 */
	@Transactional(readOnly = true)
	public NextStep of(User user) {
		if (!user.isOnboarded()) {
			return NextStep.ONBOARDING;
		}
		Optional<Long> currentTermId = termService.findCurrentTermId();
		if (currentTermId.isEmpty()) {
			return NextStep.HOME;
		}
		return burrowEntranceRepository.findTermIdByUserId(user.getId())
				.filter(termId -> !termId.equals(currentTermId.get()))
				.map(termId -> NextStep.NEW_TERM)
				.orElse(NextStep.HOME);
	}

}
