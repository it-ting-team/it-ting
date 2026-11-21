package com.itting.global.config;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * 응답 JSON의 공통 모양을 맞춘다({@code docs/api-convention.md}의 "JSON 필드").
 */
@Configuration
public class JacksonConfig {

	/**
	 * 시각은 UTC의 초 단위 ISO 8601({@code 2026-11-18T08:01:05Z})로 준다.
	 * DB는 마이크로초까지 저장하지만 API 규칙의 시각 모양에는 초 아래 자리가 없다.
	 */
	@Bean
	SimpleModule instantModule() {
		return new SimpleModule("it-ting-instant").addSerializer(Instant.class, new InstantSerializer());
	}

	static class InstantSerializer extends ValueSerializer<Instant> {

		@Override
		public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
			generator.writeString(DateTimeFormatter.ISO_INSTANT.format(value.truncatedTo(ChronoUnit.SECONDS)));
		}

	}

}
