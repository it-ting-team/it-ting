package com.itting.global.error;

import com.itting.global.response.ApiError;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 컨트롤러에 닿기 전이나 밖에서 난 실패(필터의 예외, 서블릿 컨테이너가 거절한 요청)도
 * 응답 틀로 답한다. 스프링 부트의 기본 오류 응답({@code timestamp}, {@code path} 등)을 대신한다.
 */
@Hidden
@RestController
public class ApiErrorController implements ErrorController {

	@RequestMapping("${server.error.path:/error}")
	ResponseEntity<ApiResult<Void>> error(HttpServletRequest request) {
		Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		CommonErrorCode code = CommonErrorCode.fromStatus(status instanceof Integer value ? value : 500);
		ApiError error = new ApiError(code.code(), code.message(), null, null);
		return ResponseEntity.status(code.status()).body(ApiResult.fail(error));
	}

}
