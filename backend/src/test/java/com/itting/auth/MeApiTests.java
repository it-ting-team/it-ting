package com.itting.auth;

import static com.itting.support.TestUsers.lastSessionCookieHeader;
import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/me}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MeApiTests {

	private static final String PASSWORD = "mole1234!";

	private static final String UNAUTHENTICATED = """
			{
			  "success": false,
			  "data": null,
			  "error": { "code": "UNAUTHENTICATED", "message": "로그인이 필요합니다.", "fields": null, "detail": null }
			}
			""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestUsers testUsers;

	@Test
	void 로그인한_계정의_다음_화면과_소속을_준다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		long floorId = testUsers.seoulFloorId(2);

		mockMvc.perform(get("/api/v1/me").cookie(login()))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": {
						    "userId": %d,
						    "nextStep": "ONBOARDING",
						    "campusName": "서울",
						    "generation": 15,
						    "floor": 2,
						    "campusFloorId": %d
						  },
						  "error": null
						}
						""".formatted(userId, floorId), JsonCompareMode.STRICT));
	}

	@Test
	void 온보딩을_마친_계정은_HOME이다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login();
		testUsers.markOnboarded(userId);

		mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{ "data": { "nextStep": "HOME" } }
						""", JsonCompareMode.LENIENT));
	}

	@Test
	void 세션이_있는_요청마다_쿠키를_다시_14일로_준다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login();

		MockHttpServletResponse response = mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isOk())
				.andReturn().getResponse();

		assertThat(lastSessionCookieHeader(response))
				.startsWith("ITTING_SESSION=" + session.getValue() + ";")
				.contains("Max-Age=1209600", "Secure", "HttpOnly", "SameSite=Lax");
	}

	@Test
	void 로그인하지_않았으면_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(get("/api/v1/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json(UNAUTHENTICATED, JsonCompareMode.STRICT));
	}

	@Test
	void 없거나_만료된_세션_쿠키는_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(get("/api/v1/me").cookie(new Cookie(TestUsers.SESSION_COOKIE, "bm8tc3VjaC1zZXNzaW9u")))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json(UNAUTHENTICATED, JsonCompareMode.STRICT));
	}

	@Test
	void 세션의_계정이_탈퇴했으면_세션을_지우고_401_UNAUTHENTICATED다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login();
		testUsers.withdraw(userId);

		MockHttpServletResponse response = mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json(UNAUTHENTICATED, JsonCompareMode.STRICT))
				.andReturn().getResponse();

		assertThat(lastSessionCookieHeader(response)).contains("Max-Age=0");
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
