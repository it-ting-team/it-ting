package com.itting.user.repository;

import com.itting.user.domain.UserStatus;

/**
 * 사용자 표시를 만들 값 한 줄. 프로필이 없는 계정(탈퇴)은 프로필 · 캠퍼스 값이 모두 null이다.
 */
public record UserSummaryRow(Long userId, UserStatus status, String nickname, String profileImageUrl,
		String moleHead, String moleFace, String moleNeck, String moleHand, String campusName, Short generation,
		Integer floor) {
}
