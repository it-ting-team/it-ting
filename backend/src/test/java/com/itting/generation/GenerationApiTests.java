package com.itting.generation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/v1/generations}. 기수는 서버 설정 {@code itting.generations}(15, 16)에서 온다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GenerationApiTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 로그인_없이_설정의_기수를_큰_것부터_준다() throws Exception {
		mockMvc.perform(get("/api/v1/generations"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": { "items": [ { "generation": 16 }, { "generation": 15 } ] },
						  "error": null
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void 조회_말고_다른_메서드는_405_METHOD_NOT_ALLOWED다() throws Exception {
		mockMvc.perform(post("/api/v1/generations"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
	}

}
