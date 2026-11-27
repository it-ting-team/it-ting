package com.itting.global.error;

import com.itting.global.response.ApiError;
import com.itting.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 컨트롤러에 닿기 전이나 밖에서 난 실패(필터의 예외, 서블릿 컨테이너가 거절한 요청)도
 * 응답 틀로 답한다. 스프링 부트의 기본 오류 응답({@code timestamp}, {@code path} 등)을 대신한다.
 *
 * <p>이미 나가기 시작한 응답에는 답하지 않는다. 열어 둔 실시간 소식 연결을 끝내다 실패한 경우다. 응답 틀을 덧붙일 수 없으므로
 * 원인만 로그에 남긴다.
 */
@Hidden
@RestController
public class ApiErrorController implements ErrorController {

	private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

	@RequestMapping("${server.error.path:/error}")
	ResponseEntity<ApiResult<Void>> error(HttpServletRequest request, HttpServletResponse response) {
		if (response.isCommitted()) {
			log.warn("응답을 시작한 뒤에 요청이 실패했습니다. uri={}",
					request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI),
					(Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
			return null;
		}
		Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		CommonErrorCode code = CommonErrorCode.fromStatus(status instanceof Integer value ? value : 500);
		ApiError error = new ApiError(code.code(), code.message(), null, null);
		return ResponseEntity.status(code.status()).body(ApiResult.fail(error));
	}

}
