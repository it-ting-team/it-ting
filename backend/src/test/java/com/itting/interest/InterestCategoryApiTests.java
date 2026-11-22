package com.itting.interest;

import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/interest-categories}. 처음 값은 마이그레이션이 넣은 대분류 12개 · 세부 관심사 90개다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class InterestCategoryApiTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Test
	void 대분류_12개와_세부_관심사_90개를_명세_표_순서대로_준다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);

		mockMvc.perform(get("/api/v1/interest-categories").cookie(login("mole@example.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items[*].name").value(contains("음식·음료", "스포츠", "문화생활", "게임",
						"음악·댄스", "아웃도어", "외국어·자기계발", "책·글", "사진·영상", "미술·수공예", "덕질·수집", "반려동물·식물")))
				.andExpect(jsonPath("$.data.items[*].interests[*]", hasSize(90)))
				.andExpect(jsonPath("$.data.items[0].interests[*].name").value(contains("맛집탐방", "카페/커피",
						"주류/와인/위스키", "다이어트/건강식", "요리/레시피", "베이킹/디저트", "차/다도")))
				.andExpect(jsonPath("$.data.items[1].interests", hasSize(11)))
				.andExpect(jsonPath("$.data.items[11].interests[5].name").value("곤충/파충류/기타"))
				.andExpect(jsonPath("$.data.items[0].interests[0].userCount").value(0));
	}

	@Test
	void 세부_관심사마다_고른_사람_수를_준다() throws Exception {
		long first = testUsers.createEmailUser("mole@example.com", PASSWORD);
		long second = testUsers.createEmailUser("mole2@example.com", PASSWORD);
		long running = interestId("러닝/마라톤");
		long climbing = interestId("클라이밍");
		insertUserInterest(first, running, 1);
		insertUserInterest(first, climbing, 2);
		insertUserInterest(second, running, 1);

		mockMvc.perform(get("/api/v1/interest-categories").cookie(login("mole@example.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[1].name").value("스포츠"))
				.andExpect(jsonPath("$.data.items[1].interests[0].name").value("헬스/필라테스/요가"))
				.andExpect(jsonPath("$.data.items[1].interests[0].userCount").value(0))
				.andExpect(jsonPath("$.data.items[1].interests[1].id").value(running))
				.andExpect(jsonPath("$.data.items[1].interests[1].userCount").value(2))
				.andExpect(jsonPath("$.data.items[1].interests[2].id").value(climbing))
				.andExpect(jsonPath("$.data.items[1].interests[2].userCount").value(1));
	}

	@Test
	void 고른_순서가_1부터_10_밖이면_DB가_막는다() {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		long running = interestId("러닝/마라톤");

		assertThatThrownBy(() -> insertUserInterest(userId, running, 0)).isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> insertUserInterest(userId, running, 11)).isInstanceOf(DataAccessException.class);
	}

	@Test
	void 지운_대분류와_세부_관심사는_빼고_세부_관심사가_없는_대분류는_빈_목록이다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		jdbcTemplate.update("UPDATE interest_category SET deleted_at = UTC_TIMESTAMP(6) WHERE name = '게임'");
		jdbcTemplate.update("UPDATE interest SET deleted_at = UTC_TIMESTAMP(6) WHERE name = '맛집탐방'");
		jdbcTemplate.update("""
				INSERT INTO interest_category (name, created_at, updated_at)
				VALUES ('새대분류', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""");

		mockMvc.perform(get("/api/v1/interest-categories").cookie(login("mole@example.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(12)))
				.andExpect(jsonPath("$.data.items[*].name").value(not(hasItem("게임"))))
				.andExpect(jsonPath("$.data.items[0].interests", hasSize(6)))
				.andExpect(jsonPath("$.data.items[0].interests[0].name").value("카페/커피"))
				.andExpect(jsonPath("$.data.items[11].name").value("새대분류"))
				.andExpect(jsonPath("$.data.items[11].interests", hasSize(0)));
	}

	@Test
	void 로그인하지_않았으면_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(get("/api/v1/interest-categories"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": { "code": "UNAUTHENTICATED", "message": "로그인이 필요합니다.", "fields": null, "detail": null }
						}
						""", JsonCompareMode.STRICT));
	}

	private long interestId(String name) {
		return jdbcTemplate.queryForObject("SELECT id FROM interest WHERE name = ?", Long.class, name);
	}

	private void insertUserInterest(long userId, long interestId, int sortOrder) {
		jdbcTemplate.update("""
				INSERT INTO user_interest (user_id, interest_id, sort_order, created_at, updated_at)
				VALUES (?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", userId, interestId, sortOrder);
	}

	private Cookie login(String email) throws Exception {
		return sessionCookie(mockMvc.perform(post("/api/v1/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "%s", "password": "%s" }
								""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

}
