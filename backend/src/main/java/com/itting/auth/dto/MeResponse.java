package com.itting.auth.dto;

import com.itting.auth.domain.NextStep;

/**
 * {@code GET /api/v1/me}의 {@code data}. 소속은 프로필의 지금 값이다.
 *
 * @param userId 로그인한 계정의 id
 * @param nextStep 다음에 갈 화면
 * @param campusName 프로필의 캠퍼스-층이 속한 캠퍼스 이름
 * @param generation 기수
 * @param floor 프로필의 층
 * @param campusFloorId 프로필의 캠퍼스-층 id
 */
public record MeResponse(
		Long userId,
		NextStep nextStep,
		String campusName,
		int generation,
		int floor,
		Long campusFloorId) {
}
