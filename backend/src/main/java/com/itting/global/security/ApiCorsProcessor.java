package com.itting.global.security;

import java.io.IOException;

import com.itting.global.error.CommonErrorCode;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

/**
 * 허용하지 않은 출처의 요청을 거절할 때 일반 텍스트({@code Invalid CORS request}) 대신
 * 응답 틀의 {@code 403 FORBIDDEN}으로 답한다.
 */
class ApiCorsProcessor extends DefaultCorsProcessor {

	private final SecurityErrorWriter errorWriter;

	ApiCorsProcessor(SecurityErrorWriter errorWriter) {
		this.errorWriter = errorWriter;
	}

	@Override
	protected void rejectRequest(ServerHttpResponse response) throws IOException {
		if (response instanceof ServletServerHttpResponse servletResponse) {
			errorWriter.write(servletResponse.getServletResponse(), CommonErrorCode.FORBIDDEN);
			return;
		}
		super.rejectRequest(response);
	}

}
