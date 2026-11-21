package com.itting.global.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import com.itting.support.AuditSample;
import com.itting.support.AuditSampleRepository;
import com.itting.support.MutableClock;
import com.itting.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import({TestcontainersConfiguration.class, BaseTimeEntityTests.FixedClockConfig.class})
@Transactional
class BaseTimeEntityTests {

	// 나노초까지 있는 시각. DB에는 마이크로초까지만 남는다.
	static final Instant NOW = Instant.parse("2026-11-18T08:01:05.123456789Z");
	static final Instant NOW_IN_MICROS = Instant.parse("2026-11-18T08:01:05.123456Z");

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}

	}

	@Autowired
	MutableClock clock;

	@Autowired
	AuditSampleRepository repository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void resetClock() {
		clock.setNow(NOW);
	}

	@Test
	void 저장하면_만든_시각과_바꾼_시각이_채워진다() {
		AuditSample saved = repository.saveAndFlush(new AuditSample("처음"));

		assertThat(saved.getCreatedAt()).isEqualTo(NOW_IN_MICROS);
		assertThat(saved.getUpdatedAt()).isEqualTo(NOW_IN_MICROS);
	}

	@Test
	void 바꾸면_바꾼_시각만_바뀐다() {
		AuditSample saved = repository.saveAndFlush(new AuditSample("처음"));

		clock.advance(Duration.ofHours(1));
		saved.rename("바꿈");
		repository.saveAndFlush(saved);

		assertThat(saved.getCreatedAt()).isEqualTo(NOW_IN_MICROS);
		assertThat(saved.getUpdatedAt()).isEqualTo(NOW_IN_MICROS.plus(Duration.ofHours(1)));
	}

	@Test
	void DB에는_UTC_시각으로_저장된다() {
		AuditSample saved = repository.saveAndFlush(new AuditSample("처음"));

		// 서버(JVM)의 시간대가 한국이어도 DB에는 UTC 그대로 들어가야 한다.
		String stored = jdbcTemplate.queryForObject(
				"SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') FROM audit_sample WHERE id = ?",
				String.class, saved.getId());

		assertThat(stored).isEqualTo("2026-11-18 08:01:05.123456");
	}

	@Test
	void DB에서_다시_읽어도_같은_시각이다() {
		Long id = repository.saveAndFlush(new AuditSample("처음")).getId();
		entityManager.clear();

		AuditSample found = repository.findById(id).orElseThrow();

		assertThat(found.getCreatedAt()).isEqualTo(NOW_IN_MICROS);
		assertThat(found.getUpdatedAt()).isEqualTo(NOW_IN_MICROS);
	}

}
