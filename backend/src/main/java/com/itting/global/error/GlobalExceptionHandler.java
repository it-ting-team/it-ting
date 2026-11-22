package com.itting.global.error;

import java.util.ArrayList;
import java.util.List;

import com.itting.global.response.ApiError;
import com.itting.global.response.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;

/**
 * 컨트롤러에서 나온 예외를 응답 틀({@code success}, {@code data}, {@code error})로 바꾼다.
 * 컨트롤러 밖(필터, 서블릿)에서 난 실패는 {@link ApiErrorController}가 같은 틀로 답한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException e) {
		ErrorCode code = e.getErrorCode();
		return respond(code, null, e.getFields(), e.getDetail());
	}

	/** {@code @Valid @RequestBody}, {@code @Valid @ModelAttribute}의 검증 실패. */
	@ExceptionHandler(BindException.class)
	ResponseEntity<ApiResult<Void>> handleBind(BindException e) {
		List<ApiError.FieldError> fields = e.getFieldErrors().stream()
				.map(GlobalExceptionHandler::toField)
				.toList();
		return invalidInput(fields);
	}

	/** {@code @RequestParam @Max(50) int size}처럼 메서드 인자에 붙인 검증의 실패. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ApiResult<Void>> handleMethodValidation(HandlerMethodValidationException e) {
		List<ApiError.FieldError> fields = new ArrayList<>();
		for (ParameterValidationResult result : e.getParameterValidationResults()) {
			if (result instanceof ParameterErrors errors) {
				errors.getFieldErrors().forEach(error -> fields.add(toField(error)));
				continue;
			}
			String name = parameterName(result.getMethodParameter());
			result.getResolvableErrors()
					.forEach(error -> fields.add(new ApiError.FieldError(name, error.getDefaultMessage())));
		}
		return invalidInput(fields);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiResult<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return invalidInput(List.of(new ApiError.FieldError(e.getName(), INVALID_FORMAT)));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ApiResult<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
		return invalidInput(List.of(new ApiError.FieldError(e.getParameterName(), "필수 값입니다.")));
	}

	/** 본문이 JSON이 아니거나, 필드의 타입이 틀렸거나, enum 목록에 없는 값이 왔을 때. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiResult<Void>> handleNotReadable(HttpMessageNotReadableException e) {
		String path = jsonPath(e);
		if (path == null) {
			return respond(CommonErrorCode.INVALID_INPUT, "요청 본문을 읽을 수 없습니다.", null, null);
		}
		return invalidInput(List.of(new ApiError.FieldError(path, INVALID_FORMAT)));
	}

	@ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
	ResponseEntity<ApiResult<Void>> handleNotFound(Exception e) {
		return respond(CommonErrorCode.NOT_FOUND, null, null);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiResult<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		HttpHeaders headers = new HttpHeaders();
		if (e.getSupportedHttpMethods() != null) {
			headers.setAllow(e.getSupportedHttpMethods());
		}
		return ResponseEntity.status(CommonErrorCode.METHOD_NOT_ALLOWED.status())
				.headers(headers)
				.body(body(CommonErrorCode.METHOD_NOT_ALLOWED, null, null, null));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiResult<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
		return respond(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, null, null);
	}

	/**
	 * 위에서 다루지 않은 예외. 스프링이 상태 코드를 정해 둔 예외(4xx)는 공통 코드로 바꾸고,
	 * 그 밖은 서버 오류로 답한다. 예외의 내용은 응답에 넣지 않고 로그에만 남긴다.
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResult<Void>> handleUnexpected(Exception e) {
		if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
			return respond(CommonErrorCode.fromStatus(errorResponse.getStatusCode().value()), null, null);
		}
		log.error("처리하지 못한 예외", e);
		return respond(CommonErrorCode.INTERNAL_ERROR, null, null);
	}

	private static ResponseEntity<ApiResult<Void>> invalidInput(List<ApiError.FieldError> fields) {
		return ResponseEntity.status(CommonErrorCode.INVALID_INPUT.status())
				.body(body(CommonErrorCode.INVALID_INPUT, null, fields, null));
	}

	private static ResponseEntity<ApiResult<Void>> respond(ErrorCode code, String message, Object detail) {
		return respond(code, message, null, detail);
	}

	private static ResponseEntity<ApiResult<Void>> respond(
			ErrorCode code, String message, List<ApiError.FieldError> fields, Object detail) {
		return ResponseEntity.status(code.status()).body(body(code, message, fields, detail));
	}

	private static ApiResult<Void> body(ErrorCode code, String message, List<ApiError.FieldError> fields,
			Object detail) {
		String text = message != null ? message : code.message();
		return ApiResult.fail(new ApiError(code.code(), text, fields, detail));
	}

	private static ApiError.FieldError toField(FieldError error) {
		return new ApiError.FieldError(error.getField(), error.getDefaultMessage());
	}

	/** 요청에서 쓰는 이름. {@code @RequestParam("q")}처럼 이름을 따로 줬으면 그 이름이다. */
	private static String parameterName(MethodParameter parameter) {
		RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
		if (requestParam != null && !requestParam.name().isEmpty()) {
			return requestParam.name();
		}
		PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
		if (pathVariable != null && !pathVariable.name().isEmpty()) {
			return pathVariable.name();
		}
		return parameter.getParameterName();
	}

	/** JSON을 읽다 실패한 곳의 경로({@code mole.head}, {@code answers[1].answer}). 알 수 없으면 null. */
	private static String jsonPath(Throwable e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
				StringBuilder path = new StringBuilder();
				for (JacksonException.Reference reference : jackson.getPath()) {
					if (reference.getPropertyName() != null) {
						if (!path.isEmpty()) {
							path.append('.');
						}
						path.append(reference.getPropertyName());
					}
					else if (reference.getIndex() >= 0) {
						path.append('[').append(reference.getIndex()).append(']');
					}
				}
				return path.isEmpty() ? null : path.toString();
			}
		}
		return null;
	}

}
