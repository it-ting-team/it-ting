package com.itting.campus;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/campuses}. 처음 값은 V2 마이그레이션이 넣은 임시 목록이다.
 * 테스트마다 넣은 줄은 트랜잭션이 끝나면 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CampusApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void 로그인_없이_캠퍼스를_표시_순서대로_층은_층_번호_순으로_준다() throws Exception {
		mockMvc.perform(get("/api/v1/campuses"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.data.items[*].name").value(contains("서울", "대전", "부울경", "구미", "광주")))
				.andExpect(jsonPath("$.data.items[0].floors", hasSize(20)))
				.andExpect(jsonPath("$.data.items[0].floors[0].floor").value(1))
				.andExpect(jsonPath("$.data.items[0].floors[19].floor").value(20))
				.andExpect(jsonPath("$.data.items[0].floors[0].id").isNumber());
	}

	@Test
	void 지운_캠퍼스와_층은_빼고_층이_없는_캠퍼스는_빈_목록으로_준다() throws Exception {
		jdbcTemplate.update("""
				INSERT INTO campus (name, sort_order, created_at, updated_at, deleted_at)
				VALUES ('지운캠퍼스', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""");
		jdbcTemplate.update("""
				INSERT INTO campus (name, sort_order, created_at, updated_at)
				VALUES ('새캠퍼스', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""");
		jdbcTemplate.update("""
				UPDATE campus_floor SET deleted_at = UTC_TIMESTAMP(6)
				WHERE floor = 20 AND campus_id = (SELECT id FROM campus WHERE name = '서울')
				""");

		mockMvc.perform(get("/api/v1/campuses"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].name").value(not(hasItem("지운캠퍼스"))))
				.andExpect(jsonPath("$.data.items[0].name").value("새캠퍼스"))
				.andExpect(jsonPath("$.data.items[0].floors", hasSize(0)))
				.andExpect(jsonPath("$.data.items[1].name").value("서울"))
				.andExpect(jsonPath("$.data.items[1].floors", hasSize(19)))
				.andExpect(jsonPath("$.data.items[1].floors[18].floor").value(19));
	}

	@Test
	void 조회_말고_다른_메서드는_405_METHOD_NOT_ALLOWED다() throws Exception {
		mockMvc.perform(post("/api/v1/campuses"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
	}

}
