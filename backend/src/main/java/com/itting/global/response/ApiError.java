package com.itting.global.response;

import java.util.List;

/**
 * 실패한 응답의 {@code error}다({@code docs/api-convention.md}의 "에러").
 *
 * @param code 프론트엔드가 실패 종류를 구분하는 값
 * @param message 개발자가 읽는 한국어 설명 한 문장
 * @param fields 입력 오류일 때만 어느 필드가 왜 틀렸는지의 목록. 그 밖에는 null
 * @param detail 화면에 보여 줄 값이 실패에 딸려 있을 때만 그 값. 그 밖에는 null
 */
public record ApiError(String code, String message, List<FieldError> fields, Object detail) {

	/**
	 * @param field 요청의 필드 이름. 객체나 목록 안의 값은 {@code mole.head}, {@code answers[1].answer}처럼 경로로 적는다
	 * @param message 왜 틀렸는지
	 */
	public record FieldError(String field, String message) {
	}

}
