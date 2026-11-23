package com.itting.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사용자 표시({@code docs/api/00-common.md}의 "사용자 표시"). 사람을 보여 주는 모든 곳에 쓴다.
 * 실명, 반, 이메일은 담지 않는다. 탈퇴한 사용자는 {@code id}와 {@code isWithdrawn}만 값이 있다.
 */
public record UserSummaryResponse(
		@Schema(description = "계정의 id", example = "31") Long id,
		@Schema(description = "닉네임. 탈퇴한 사용자는 null", example = "두더지왕", nullable = true) String nickname,
		@Schema(description = "프로필 사진의 주소. 사진이 없거나 탈퇴한 사용자는 null", nullable = true) String profileImageUrl,
		@Schema(description = "두더지 아이템. 항상 네 부위를 모두 담는다") MoleResponse mole,
		@Schema(description = "캠퍼스 이름. 탈퇴한 사용자는 null", example = "서울", nullable = true) String campusName,
		@Schema(description = "기수. 탈퇴한 사용자는 null", example = "14", nullable = true) Integer generation,
		@Schema(description = "층. 탈퇴한 사용자는 null", example = "2", nullable = true) Integer floor,
		@Schema(description = "탈퇴한 사용자인지", example = "false") boolean isWithdrawn) {

	/** 탈퇴한 사용자. 이름("탈퇴한 사용자")과 기본 두더지 그림은 프론트엔드가 정한다. */
	public static UserSummaryResponse withdrawn(Long id) {
		return new UserSummaryResponse(id, null, null, MoleResponse.EMPTY, null, null, null, true);
	}

}
