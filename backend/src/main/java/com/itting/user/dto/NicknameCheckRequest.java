package com.itting.user.dto;

/**
 * {@code POST /api/v1/nicknames/check}의 본문.
 *
 * @param nickname 확인할 닉네임. 앞뒤 공백은 지운다
 */
public record NicknameCheckRequest(@Nickname String nickname) {

	public NicknameCheckRequest {
		if (nickname != null) {
			nickname = nickname.strip();
		}
	}

}
