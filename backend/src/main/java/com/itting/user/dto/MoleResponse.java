package com.itting.user.dto;

import com.itting.user.domain.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 두더지가 걸친 아이템. 사용자 표시의 {@code mole}이고, {@code PATCH /api/v1/me/mole}의 응답이다.
 * 항상 네 부위를 모두 담고, 고르지 않은 부위는 null이다. 코드마다의 그림은 프론트엔드가 갖는다.
 */
public record MoleResponse(
		@Schema(description = "머리 아이템 코드. 없으면 null", example = "CAP_RED", nullable = true) String head,
		@Schema(description = "얼굴 아이템 코드. 없으면 null", example = "GLASSES_ROUND", nullable = true) String face,
		@Schema(description = "목 아이템 코드. 없으면 null", example = "SCARF_GREEN", nullable = true) String neck,
		@Schema(description = "손 아이템 코드. 없으면 null", nullable = true) String hand) {

	/** 탈퇴한 사용자의 두더지. 네 부위가 모두 null이다. */
	public static final MoleResponse EMPTY = new MoleResponse(null, null, null, null);

	public static MoleResponse from(UserProfile profile) {
		return new MoleResponse(profile.getMoleHead(), profile.getMoleFace(), profile.getMoleNeck(),
				profile.getMoleHand());
	}

}
