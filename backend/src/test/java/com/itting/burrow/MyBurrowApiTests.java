package com.itting.burrow;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.itting.support.Members;
import com.itting.support.Members.Member;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/v1/me/burrow}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBurrowApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Autowired
	OnboardingSteps steps;

	Members members;

	@BeforeEach
	void setUp() {
		steps.clearNicknameHolds();
		members = new Members(mockMvc, testUsers, steps, jdbcTemplate);
	}

	@Test
	void 온보딩을_마친_회원은_내_땅굴과_내_정보를_한_번에_받는다() throws Exception {
		List<Long> interests = steps.interestIds(3);
		List<Long> questions = steps.questionIds(3);
		Member mole = members.onboard("mole@example.com", "두더지왕",
				List.of(interests.get(2), interests.get(0), interests.get(1)),
				List.of(questions.get(1), questions.get(2), questions.get(0)));
		Member visitor = members.onboard("visitor@example.com", "새벽산책");
		Instant now = Instant.parse("2026-11-20T00:00:00Z");
		members.writeGuestbook(mole.burrowId(), visitor.userId(), "반가워요", now);
		members.writeGuestbook(mole.burrowId(), visitor.userId(), "또 올게요", now.plusSeconds(60));
		members.writeHiddenGuestbook(mole.burrowId(), visitor.userId(), "가려진 글", now.plusSeconds(120));

		mockMvc.perform(get("/api/v1/me/burrow").cookie(mole.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.burrowId").value(mole.burrowId()))
				.andExpect(jsonPath("$.data.owner.id").value(mole.userId()))
				.andExpect(jsonPath("$.data.owner.nickname").value("두더지왕"))
				.andExpect(jsonPath("$.data.owner.profileImageUrl").isEmpty())
				.andExpect(jsonPath("$.data.owner.mole.head").value("CAP_RED"))
				.andExpect(jsonPath("$.data.owner.mole.face").isEmpty())
				.andExpect(jsonPath("$.data.owner.mole.neck").value("SCARF_GREEN"))
				.andExpect(jsonPath("$.data.owner.mole.hand").isEmpty())
				.andExpect(jsonPath("$.data.owner.campusName").value("서울"))
				.andExpect(jsonPath("$.data.owner.generation").value(15))
				.andExpect(jsonPath("$.data.owner.floor").value(2))
				.andExpect(jsonPath("$.data.owner.isWithdrawn").value(false))
				.andExpect(jsonPath("$.data.campusFloorId").value(testUsers.seoulFloorId(2)))
				.andExpect(jsonPath("$.data.intro").isEmpty())
				.andExpect(jsonPath("$.data.interests[*].id").value(contains(
						interests.get(2).intValue(), interests.get(0).intValue(), interests.get(1).intValue())))
				.andExpect(jsonPath("$.data.interests[0].name").value(interestName(interests.get(2))))
				.andExpect(jsonPath("$.data.interests[0].interestCategoryId").value(1))
				.andExpect(jsonPath("$.data.interests[*].isShared").value(contains(false, false, false)))
				.andExpect(jsonPath("$.data.answers[*].slot").value(contains(1, 2, 3)))
				.andExpect(jsonPath("$.data.answers[*].introQuestionId").value(contains(
						questions.get(1).intValue(), questions.get(2).intValue(), questions.get(0).intValue())))
				.andExpect(jsonPath("$.data.answers[0].question").value(questionContent(questions.get(1))))
				.andExpect(jsonPath("$.data.answers[0].answer")
						.value("질문 %d에 대한 열 글자 넘는 답".formatted(questions.get(1))))
				.andExpect(jsonPath("$.data.regularCount").value(0))
				.andExpect(jsonPath("$.data.guestbookCount").value(2))
				.andExpect(jsonPath("$.data.entrance.termId").value(steps.currentTermId()))
				.andExpect(jsonPath("$.data.entrance.campusFloorId").value(testUsers.seoulFloorId(2)))
				.andExpect(jsonPath("$.data.entrance.slot").value(1))
				.andExpect(jsonPath("$.error").isEmpty());
	}

	@Test
	void 방명록_글이_없으면_글_수는_0이다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");

		mockMvc.perform(get("/api/v1/me/burrow").cookie(mole.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.guestbookCount").value(0));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/burrow"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 온보딩을_마치지_않았으면_403이다() throws Exception {
		testUsers.createEmailUser("mole@example.com", Members.PASSWORD);

		mockMvc.perform(get("/api/v1/me/burrow").cookie(members.login("mole@example.com")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	@Test
	void 새_학기를_고르지_않았으면_403이다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");
		steps.openNewTerm();

		mockMvc.perform(get("/api/v1/me/burrow").cookie(mole.session()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("NEW_TERM_INCOMPLETE"));
	}

	private String interestName(long interestId) {
		return jdbcTemplate.queryForObject("SELECT name FROM interest WHERE id = ?", String.class, interestId);
	}

	private String questionContent(long questionId) {
		return jdbcTemplate.queryForObject("SELECT content FROM intro_question WHERE id = ?", String.class,
				questionId);
	}

}
