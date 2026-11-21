package com.itting.global.config;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 엔티티의 created_at, updated_at을 자동으로 채운다.
 * 시각은 {@link Clock}에서 가져오므로 테스트에서 시계를 바꿔 검증할 수 있다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * DB 칼럼(DATETIME(6))이 마이크로초까지 저장하므로 같은 정밀도로 자른다.
	 * 자르지 않으면 저장 직후 엔티티의 값과 DB에서 다시 읽은 값이 달라진다.
	 */
	@Bean
	public DateTimeProvider auditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
	}

}
