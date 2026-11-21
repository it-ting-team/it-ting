package com.itting.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.itting.global.error.BusinessException;
import com.itting.global.error.ErrorCode;
import com.itting.global.response.ApiResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 응답 틀과 예외 처리를 검증하려고 테스트에서만 띄우는 컨트롤러다.
 */
@RestController
@RequestMapping("/api/v1/error-samples")
public class ErrorSampleController {

	@GetMapping
	ApiResult<SampleResponse> ok() {
		return ApiResult.ok(new SampleResponse(1L, Instant.parse("2026-11-18T08:01:05.123456Z"), null, List.of()));
	}

	@PostMapping
	ApiResult<Void> create(@Valid @RequestBody SampleRequest request) {
		return ApiResult.ok();
	}

	@GetMapping("/search")
	ApiResult<Void> search(@RequestParam @Min(1) @Max(50) int size) {
		return ApiResult.ok();
	}

	@GetMapping("/business")
	ApiResult<Void> business() {
		throw new BusinessException(SampleErrorCode.SAMPLE_SUSPENDED, Map.of("reason", "도배"));
	}

	@GetMapping("/crash")
	ApiResult<Void> crash() {
		throw new IllegalStateException("SELECT * FROM user WHERE password = 'secret'");
	}

	public record SampleResponse(Long id, Instant createdAt, String memo, List<String> tags) {
	}

	public record SampleRequest(
			@NotBlank @Size(max = 12) String nickname,
			@NotNull @Valid Mole mole,
			@NotNull SampleType type,
			List<@Valid Answer> answers) {
	}

	public record Mole(@Size(max = 5) String head) {
	}

	public record Answer(@NotBlank String answer) {
	}

	public enum SampleType {
		INTEREST, TMI
	}

	enum SampleErrorCode implements ErrorCode {

		SAMPLE_SUSPENDED;

		@Override
		public HttpStatus status() {
			return HttpStatus.FORBIDDEN;
		}

		@Override
		public String message() {
			return "정지된 계정입니다.";
		}

	}

}
