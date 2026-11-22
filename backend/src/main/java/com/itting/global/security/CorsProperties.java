package com.itting.global.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 쿠키를 실어 API를 부를 수 있는 프론트엔드 출처.
 *
 * @param allowedOrigins {@code itting.cors.allowed-origins}. 예: {@code http://localhost:3000}
 */
@ConfigurationProperties("itting.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
	}

}
