package com.itting.question;

import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
 * {@code GET /api/v1/intro-questions}. 처음 값은 마이그레이션이 넣은 관심사 질문 24개, TMI 질문 16개다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class IntroQuestionApiTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@BeforeEach
	void setUp() {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
	}

	@Test
	void type이_없으면_두_종류를_모두_id_순으로_준다() throws Exception {
		mockMvc.perform(get("/api/v1/intro-questions").cookie(login()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items", hasSize(40)))
				.andExpect(jsonPath("$.data.items[0].id").isNumber())
				.andExpect(jsonPath("$.data.items[0].type").value("INTEREST"))
				.andExpect(jsonPath("$.data.items[0].interestCategoryId").value(1))
				.andExpect(jsonPath("$.data.items[0].content").value("요즘 가장 자주 가는 맛집이나 카페는?"))
				.andExpect(jsonPath("$.data.items[24].type").value("TMI"))
				.andExpect(jsonPath("$.data.items[24].interestCategoryId").value(nullValue()))
				.andExpect(jsonPath("$.data.items[24].content").value("최근에 산 것 가운데 가장 잘 산 것은?"));
	}

	@Test
	void type을_보내면_그_종류만_준다() throws Exception {
		Cookie session = login();

		mockMvc.perform(get("/api/v1/intro-questions").param("type", "TMI").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(16)))
				.andExpect(jsonPath("$.data.items[*].type", everyItem(is("TMI"))))
				.andExpect(jsonPath("$.data.items[*].interestCategoryId", everyItem(nullValue())));

		mockMvc.perform(get("/api/v1/intro-questions").param("type", "INTEREST").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(24)))
				.andExpect(jsonPath("$.data.items[*].type", everyItem(is("INTEREST"))));
	}

	@Test
	void type이_목록에_없는_값이면_400_INVALID_INPUT이다() throws Exception {
		mockMvc.perform(get("/api/v1/intro-questions").param("type", "FOO").cookie(login()))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "INVALID_INPUT",
						    "message": "입력값이 올바르지 않습니다.",
						    "fields": [ { "field": "type", "message": "형식이 올바르지 않습니다." } ],
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 로그인하지_않았으면_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(get("/api/v1/intro-questions"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 관심사_질문에_대분류가_없거나_TMI_질문에_대분류가_있으면_DB가_막는다() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO intro_question (type, interest_category_id, content, created_at, updated_at)
				VALUES ('INTEREST', NULL, '대분류 없는 관심사 질문', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""")).isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO intro_question (type, interest_category_id, content, created_at, updated_at)
				VALUES ('TMI', 1, '대분류 있는 TMI 질문', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""")).isInstanceOf(DataAccessException.class);
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
