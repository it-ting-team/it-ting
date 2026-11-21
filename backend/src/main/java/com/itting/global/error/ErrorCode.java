package com.itting.global.error;

import org.springframework.http.HttpStatus;

/**
 * 실패의 종류다. 영역마다 이 인터페이스를 구현한 enum을 두고({@code MeetupErrorCode} 등),
 * enum 상수의 이름이 응답의 {@code code}가 된다.
 *
 * <p>이름은 영어 대문자와 밑줄로 {@code <대상>_<상태>} 모양으로 짓는다({@code MEETUP_NOT_FOUND}).
 * 같은 실패에는 어느 API에서나 같은 코드를 쓴다.
 */
public interface ErrorCode {

	/** enum 상수의 이름. enum이 구현하면 따로 만들 필요가 없다. */
	String name();

	HttpStatus status();

	/** 개발자가 읽는 한국어 설명 한 문장. 화면 문구는 프론트엔드가 code를 보고 정한다. */
	String message();

	default String code() {
		return name();
	}

}
