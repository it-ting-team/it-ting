package com.itting.term;

import static com.itting.support.TestUsers.sessionCookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.itting.support.MutableClock;
import com.itting.support.MutableClockConfiguration;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/terms}. 처음 값은 마이그레이션이 넣은 2026 하반기(2026-07-01 00:00 한국 시간에 열림) 하나다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MutableClockConfiguration.class })
@Transactional
class TermApiTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Autowired
	MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.setNow(Instant.parse("2026-11-23T00:00:00Z"));
		testUsers.createEmailUser("mole@example.com", PASSWORD);
	}

	@Test
	void 열린_학기만_최근에_열린_것부터_주고_첫_번째가_이번_학기다() throws Exception {
		insertTerm(2026, 1, "2025-12-31 15:00:00");
		insertTerm(2027, 1, "2026-12-31 15:00:00");
		long secondHalf2026 = termId(2026, 2);
		long firstHalf2026 = termId(2026, 1);

		mockMvc.perform(get("/api/v1/terms").cookie(login()))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": {
						    "items": [
						      { "id": %d, "year": 2026, "half": 2, "isCurrent": true },
						      { "id": %d, "year": 2026, "half": 1, "isCurrent": false }
						    ]
						  },
						  "error": null
						}
						""".formatted(secondHalf2026, firstHalf2026), JsonCompareMode.STRICT));
	}

	@Test
	void 연_시각이_지나면_새_학기가_이번_학기가_된다() throws Exception {
		insertTerm(2027, 1, "2026-12-31 15:00:00");
		Cookie session = login();
		clock.setNow(Instant.parse("2026-12-31T15:00:00Z"));

		mockMvc.perform(get("/api/v1/terms").cookie(session))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "data": {
						    "items": [
						      { "year": 2027, "half": 1, "isCurrent": true },
						      { "year": 2026, "half": 2, "isCurrent": false }
						    ]
						  }
						}
						""", JsonCompareMode.LENIENT));
	}

	@Test
	void 열린_학기가_없으면_빈_목록이다() throws Exception {
		jdbcTemplate.update("DELETE FROM term");

		mockMvc.perform(get("/api/v1/terms").cookie(login()))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "success": true, "data": { "items": [] }, "error": null }
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 로그인하지_않았으면_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(get("/api/v1/terms"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": { "code": "UNAUTHENTICATED", "message": "로그인이 필요합니다.", "fields": null, "detail": null }
						}
						""", JsonCompareMode.STRICT));
	}

	private void insertTerm(int year, int half, String openedAtUtc) {
		jdbcTemplate.update("""
				INSERT INTO term (year, half, opened_at, created_at, updated_at)
				VALUES (?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", year, half, openedAtUtc);
	}

	private long termId(int year, int half) {
		return jdbcTemplate.queryForObject("SELECT id FROM term WHERE year = ? AND half = ?", Long.class, year, half);
	}

	private Cookie login() throws Exception {
		return sessionCookie(mockMvc.perform(post("/api/v1/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "mole@example.com", "password": "%s" }
								""".formatted(PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

}
