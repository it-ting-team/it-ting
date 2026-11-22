package com.itting.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 검사와 CORS. 허용 출처는 이 테스트에서 정한다.
 */
@SpringBootTest(properties = "itting.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityConfigTests {

	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	private static final String UNAUTHENTICATED = """
			{
			  "success": false,
			  "data": null,
			  "error": { "code": "UNAUTHENTICATED", "message": "로그인이 필요합니다.", "fields": null, "detail": null }
			}
			""";

	@Autowired
	MockMvc mockMvc;

	@Test
	void 허용_목록의_API와_Swagger_헬스체크는_로그인_없이_부른다() throws Exception {
		mockMvc.perform(get("/api/v1/campuses")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/generations")).andExpect(status().isOk());
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void 로그인하지_않고_없는_API_주소를_부르면_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(get("/api/v1/nothing"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json(UNAUTHENTICATED, JsonCompareMode.STRICT));
		mockMvc.perform(post("/api/v1/nothing").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json(UNAUTHENTICATED, JsonCompareMode.STRICT));
	}

	@Test
	void 메서드를_적어_연_주소는_그_메서드만_로그인_없이_부른다() throws Exception {
		mockMvc.perform(get("/api/v1/users"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json(UNAUTHENTICATED, JsonCompareMode.STRICT));
		mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser
	void 로그인했으면_없는_API_주소는_404_NOT_FOUND다() throws Exception {
		mockMvc.perform(get("/api/v1/nothing"))
				.andExpect(status().isNotFound())
				.andExpect(content().json("""
						{ "success": false, "error": { "code": "NOT_FOUND" } }
						""", JsonCompareMode.LENIENT));
	}

	@Test
	void 로그인하지_않은_요청에는_세션을_만들지_않는다() throws Exception {
		mockMvc.perform(get("/api/v1/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist("Set-Cookie"));
	}

	@Test
	void 허용한_출처의_미리_보내는_요청은_쿠키와_함께_허용한다() throws Exception {
		mockMvc.perform(options("/api/v1/me")
						.header("Origin", ALLOWED_ORIGIN)
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
		mockMvc.perform(get("/api/v1/campuses").header("Origin", ALLOWED_ORIGIN))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	@Test
	void 허용하지_않은_출처는_CORS가_403_FORBIDDEN_응답_틀로_거절한다() throws Exception {
		String forbidden = """
				{
				  "success": false,
				  "data": null,
				  "error": { "code": "FORBIDDEN", "message": "할 수 없는 요청입니다.", "fields": null, "detail": null }
				}
				""";
		mockMvc.perform(options("/api/v1/me")
						.header("Origin", "https://evil.example.com")
						.header("Access-Control-Request-Method", "DELETE"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
				.andExpect(content().json(forbidden, JsonCompareMode.STRICT));
		mockMvc.perform(get("/api/v1/campuses").header("Origin", "https://evil.example.com"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
				.andExpect(content().json(forbidden, JsonCompareMode.STRICT));
	}

}
