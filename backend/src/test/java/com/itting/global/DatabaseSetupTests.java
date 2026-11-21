package com.itting.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.itting.support.AuditSample;
import com.itting.support.AuditSampleRepository;
import com.itting.support.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatabaseSetupTests {

	@Autowired
	Flyway flyway;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	AuditSampleRepository repository;

	@Test
	void 빈_DB에_마이그레이션이_모두_적용된다() {
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().applied())
				.extracting(MigrationInfo::getScript)
				.contains("V1__init.sql");
	}

	@Test
	void DB_기본_문자셋이_utf8mb4다() {
		Map<String, Object> schema = jdbcTemplate.queryForMap("""
				SELECT DEFAULT_CHARACTER_SET_NAME AS charset, DEFAULT_COLLATION_NAME AS collation
				FROM information_schema.SCHEMATA
				WHERE SCHEMA_NAME = DATABASE()
				""");

		assertThat(schema).containsEntry("charset", "utf8mb4")
				.containsEntry("collation", "utf8mb4_0900_ai_ci");
	}

	@Test
	void DB_시간대가_UTC다() {
		String timeZone = jdbcTemplate.queryForObject("SELECT @@global.time_zone", String.class);

		assertThat(timeZone).isEqualTo("+00:00");
	}

	@Test
	void 한글과_이모지를_그대로_저장한다() {
		AuditSample saved = repository.saveAndFlush(new AuditSample("디굴디굴 🐹"));

		String name = jdbcTemplate.queryForObject(
				"SELECT name FROM audit_sample WHERE id = ?", String.class, saved.getId());

		assertThat(name).isEqualTo("디굴디굴 🐹");
	}

}
