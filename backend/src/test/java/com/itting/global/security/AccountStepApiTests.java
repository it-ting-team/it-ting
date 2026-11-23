package com.itting.global.security;

import static com.itting.support.TestUsers.sessionCookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itting.support.OnboardingSteps;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩 · 새 학기를 마치지 않은 계정을 막는 장치와 {@code nextStep}의 {@code NEW_TERM}.
 * 표시가 없는 API는 테스트용 {@code /api/v1/step-samples}를 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountStepApiTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestUsers testUsers;

	@Autowired
	OnboardingSteps steps;

	@BeforeEach
	void clearHolds() {
		steps.clearNicknameHolds();
	}

	@Test
	void 온보딩_중에는_표시가_없는_API가_403_ONBOARDING_INCOMPLETE다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login("mole@example.com");

		mockMvc.perform(get("/api/v1/step-samples").cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": { "code": "ONBOARDING_INCOMPLETE", "message": "온보딩을 마친 뒤에 이용할 수 있습니다.", "fields": null, "detail": null }
						}
						""", JsonCompareMode.STRICT));
		mockMvc.perform(get("/api/v1/step-samples/new-term").cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	@Test
	void 온보딩_중에도_부를_수_있는_API는_통과한다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login("mole@example.com");

		mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nextStep").value("ONBOARDING"));
		for (String path : new String[] { "/api/v1/step-samples/onboarding", "/api/v1/terms",
				"/api/v1/interest-categories", "/api/v1/intro-questions", "/api/v1/campuses", "/api/v1/generations" }) {
			mockMvc.perform(get(path).cookie(session)).andExpect(status().isOk());
		}
		mockMvc.perform(post("/api/v1/nicknames/check")
						.cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "nickname": "두더지왕" }
								"""))
				.andExpect(status().isOk());
	}

	@Test
	void 온보딩과_새_학기를_마쳤으면_표시가_없는_API도_부를_수_있다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login("mole@example.com");
		completeOnboarding(session);

		mockMvc.perform(get("/api/v1/step-samples").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void 새_학기가_열리면_nextStep이_NEW_TERM이다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login("mole@example.com");
		completeOnboarding(session);

		steps.openNewTerm();

		mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nextStep").value("NEW_TERM"));
		mockMvc.perform(post("/api/v1/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "mole@example.com", "password": "%s" }
								""".formatted(PASSWORD)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.nextStep").value("NEW_TERM"));
	}

	@Test
	void 새_학기를_고르기_전에는_표시가_없는_API와_온보딩_API가_403_NEW_TERM_INCOMPLETE다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login("mole@example.com");
		completeOnboarding(session);
		steps.openNewTerm();

		mockMvc.perform(get("/api/v1/step-samples").cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "NEW_TERM_INCOMPLETE",
						    "message": "이번 학기의 캠퍼스와 층을 고른 뒤에 이용할 수 있습니다.",
						    "fields": null,
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
		mockMvc.perform(get("/api/v1/step-samples/onboarding").cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NEW_TERM_INCOMPLETE"));
		mockMvc.perform(post("/api/v1/nicknames/check")
						.cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "nickname": "땅굴대장" }
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NEW_TERM_INCOMPLETE"));
	}

	@Test
	void 새_학기를_고르기_전에도_부를_수_있는_API는_통과한다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login("mole@example.com");
		completeOnboarding(session);
		steps.openNewTerm();

		for (String path : new String[] { "/api/v1/step-samples/new-term", "/api/v1/terms",
				"/api/v1/interest-categories", "/api/v1/intro-questions", "/api/v1/campuses", "/api/v1/me" }) {
			mockMvc.perform(get(path).cookie(session)).andExpect(status().isOk());
		}
		mockMvc.perform(delete("/api/v1/sessions/current").cookie(session))
				.andExpect(status().isOk());
	}

	@Test
	void 로그인하지_않은_요청은_막는_장치가_아니라_로그인_검사가_답한다() throws Exception {
		mockMvc.perform(get("/api/v1/step-samples"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private void completeOnboarding(Cookie session) throws Exception {
		mockMvc.perform(post("/api/v1/me/onboarding/complete")
						.cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content(steps.completeBody("두더지왕")))
				.andExpect(status().isCreated());
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
