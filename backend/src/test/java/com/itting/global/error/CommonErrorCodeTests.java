package com.itting.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommonErrorCodeTests {

	@Test
	void 코드는_API_규칙의_정규식을_지킨다() {
		for (CommonErrorCode code : CommonErrorCode.values()) {
			assertThat(code.code()).matches("^[A-Z]+(_[A-Z]+)*$");
		}
	}

	@Test
	void 상태_코드로_공통_코드를_찾는다() {
		assertThat(CommonErrorCode.fromStatus(401)).isEqualTo(CommonErrorCode.UNAUTHENTICATED);
		assertThat(CommonErrorCode.fromStatus(405)).isEqualTo(CommonErrorCode.METHOD_NOT_ALLOWED);
		assertThat(CommonErrorCode.fromStatus(413)).isEqualTo(CommonErrorCode.INVALID_INPUT);
		assertThat(CommonErrorCode.fromStatus(503)).isEqualTo(CommonErrorCode.INTERNAL_ERROR);
	}

}
