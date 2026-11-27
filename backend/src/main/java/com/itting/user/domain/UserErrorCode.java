package com.itting.user.domain;

import com.itting.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 프로필 영역의 에러 코드. 닉네임을 확인하거나 저장하는 API(닉네임 확인, 온보딩 확정, 내 정보 수정), 프로필 팝업,
 * 비밀번호 변경, 회원 탈퇴가 쓴다.
 */
public enum UserErrorCode implements ErrorCode {

	/** 다른 사람이 쓰고 있거나 맡아 둔 닉네임. 영어 대소문자만 다른 닉네임도 같은 것으로 본다. */
	NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

	/** 없는 사람, 탈퇴한 사람, 가입(온보딩)을 마치지 않은 사람. 프로필 팝업이 쓴다. */
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

	/** 비밀번호가 없는 계정(Google로만 가입). 비밀번호 변경이 쓴다. */
	PASSWORD_NOT_SET(HttpStatus.CONFLICT, "비밀번호가 없는 계정입니다."),

	/** 지금 비밀번호가 맞지 않음. 비밀번호 변경이 쓴다. */
	CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "지금 비밀번호가 맞지 않습니다."),

	/** 새 비밀번호가 지금 비밀번호와 같음. 비밀번호 변경이 쓴다. */
	PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "새 비밀번호가 지금 비밀번호와 같습니다."),

	/** 내가 연 예정 모임이 있어 탈퇴할 수 없음. 회원 탈퇴가 쓴다. 어떤 모임인지는 탈퇴 전 확인이 준다. */
	HOSTING_MEETUP_EXISTS(HttpStatus.CONFLICT, "내가 연 예정 모임이 있어 탈퇴할 수 없습니다.");

	private final HttpStatus status;

	private final String message;

	UserErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	@Override
	public HttpStatus status() {
		return status;
	}

	@Override
	public String message() {
		return message;
	}

}
