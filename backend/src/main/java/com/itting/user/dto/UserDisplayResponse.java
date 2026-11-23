package com.itting.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사람을 보여 주는 모든 곳이 함께 쓰는 "사용자 표시"({@code docs/api/00-common.md}의 사용자 표시).
 * 글 작성자, 댓글 작성자, 대화 상대, 모임 주최자, 방명록을 쓴 사람이 이 모양이다. 실명, 반, 이메일은 담지 않는다.
 * 탈퇴한 사용자는 {@code id}와 {@code isWithdrawn}만 값이 있다.
 */
public record UserDisplayResponse(
		@Schema(description = "계정의 id", example = "31") Long id,
		@Schema(description = "닉네임. 탈퇴한 사용자는 null", example = "두더지왕") String nickname,
		@Schema(description = "프로필 사진의 주소. 올리지 않은 사람과 탈퇴한 사용자는 null") String profileImageUrl,
		@Schema(description = "두더지 아이템. 늘 네 부위를 모두 담는다") Mole mole,
		@Schema(description = "캠퍼스 이름. 탈퇴한 사용자는 null", example = "서울") String campusName,
		@Schema(description = "기수. 탈퇴한 사용자는 null", example = "14") Integer generation,
		@Schema(description = "층. 탈퇴한 사용자는 null", example = "2") Integer floor,
		@Schema(description = "탈퇴한 사용자인지", example = "false") boolean isWithdrawn) {

	/** 탈퇴한 사용자. 프로필을 지웠으므로 id 말고는 비어 있다. */
	public static UserDisplayResponse withdrawn(Long id) {
		return new UserDisplayResponse(id, null, null, Mole.EMPTY, null, null, null, true);
	}

	/** 두더지 아이템 코드. 고르지 않은 부위는 null. */
	public record Mole(
			@Schema(description = "머리 아이템 코드", example = "CAP_RED") String head,
			@Schema(description = "얼굴 아이템 코드") String face,
			@Schema(description = "목 아이템 코드", example = "SCARF_GREEN") String neck,
			@Schema(description = "손 아이템 코드") String hand) {

		static final Mole EMPTY = new Mole(null, null, null, null);

	}

}
