package com.itting.interest.service;

import com.itting.interest.repository.UserInterestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴의 관심사 영역 정리. 탈퇴 트랜잭션 안에서만 부른다. 내 관심사를 지운다. */
@Component
public class InterestWithdrawalCleaner {

	private final UserInterestRepository userInterests;

	public InterestWithdrawalCleaner(UserInterestRepository userInterests) {
		this.userInterests = userInterests;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void clean(Long userId) {
		userInterests.deleteAllByUserId(userId);
	}

}
