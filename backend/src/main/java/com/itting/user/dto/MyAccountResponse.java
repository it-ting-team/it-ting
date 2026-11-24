package com.itting.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/v1/me/account}의 {@code data}. 설정 메뉴와 Google 연동하기 화면이 쓰는 "계정의 모양"이다.
 * 비밀번호나 Google 식별자, 로그인 이메일은 담지 않는다. 있는지만 준다.
 */
public record MyAccountResponse(
		@Schema(description = "비밀번호가 있는 계정인지. 이메일로 가입한 계정은 true, Google로만 가입한 계정은 false", example = "true")
		boolean hasPassword,
		@Schema(description = "Google 계정이 연결돼 있는지", example = "true") boolean isGoogleLinked,
		@Schema(description = "연결된 Google 이메일. 연결돼 있지 않으면 null", example = "dawnwalk@gmail.com", nullable = true)
		String googleEmail,
		@Schema(description = "Google 연동을 해제할 수 있는지. 연동돼 있고 비밀번호가 있을 때만 true", example = "true")
		boolean canUnlinkGoogle) {
}
