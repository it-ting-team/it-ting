package com.itting.global.security;

import java.io.IOException;

import com.itting.global.error.ErrorCode;
import com.itting.global.response.ApiError;
import com.itting.global.response.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 필터가 막은 요청(로그인하지 않음, 권한 없음)도 응답 틀로 답한다.
 * 컨트롤러에 닿기 전이라 {@code GlobalExceptionHandler}가 다루지 못한다.
 */
@Component
public class SecurityErrorWriter {

	private final JsonMapper jsonMapper;

	public SecurityErrorWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void write(HttpServletResponse response, ErrorCode code) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(),
				ApiResult.fail(new ApiError(code.code(), code.message(), null, null)));
	}

}
