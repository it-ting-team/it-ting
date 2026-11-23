package com.itting.onboarding;

import static com.itting.support.TestUsers.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /api/v1/me/onboarding/complete}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class OnboardingApiTests {

	private static final String PASSWORD = "mole1234!";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	TestUsers testUsers;

	@Autowired
	OnboardingSteps steps;

	/** 테스트 트랜잭션 안에서는 API가 커밋하지 않으므로, SQL로 확인하기 전에 JPA가 미뤄 둔 변경을 DB에 쓴다. */
	@PersistenceContext
	EntityManager entityManager;

	@BeforeEach
	void clearHolds() {
		steps.clearNicknameHolds();
	}

	@Test
	void 처음_확정하면_201과_땅굴을_주고_정한_값을_모두_저장한다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login("mole@example.com");
		List<Long> interests = steps.interestIds(4);
		List<Long> questions = steps.questionIds(3);
		checkNickname(session, "두더지왕").andExpect(status().isOk());
		assertThat(steps.holder("두더지왕")).isEqualTo(String.valueOf(userId));

		String response = complete(session, steps.completeBody("두더지왕",
				List.of(interests.get(2), interests.get(0), interests.get(3), interests.get(1)),
				List.of(questions.get(1), questions.get(2), questions.get(0))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.userId").value(userId))
				.andExpect(jsonPath("$.data.nextStep").value("HOME"))
				.andExpect(jsonPath("$.data.burrowId").value(notNullValue()))
				.andExpect(jsonPath("$.error").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		long burrowId = ((Number) JsonPath.read(response, "$.data.burrowId")).longValue();
		entityManager.flush();

		Map<String, Object> profile = jdbcTemplate.queryForMap(
				"SELECT nickname, mole_head, mole_face, mole_neck, mole_hand FROM user_profile WHERE user_id = ?", userId);
		assertThat(profile).containsEntry("nickname", "두더지왕")
				.containsEntry("mole_head", "CAP_RED")
				.containsEntry("mole_face", null)
				.containsEntry("mole_neck", "SCARF_GREEN")
				.containsEntry("mole_hand", null);
		assertThat(jdbcTemplate.queryForList(
				"SELECT interest_id FROM user_interest WHERE user_id = ? ORDER BY sort_order", Long.class, userId))
				.containsExactly(interests.get(2), interests.get(0), interests.get(3), interests.get(1));
		assertThat(jdbcTemplate.queryForList(
				"SELECT intro_question_id FROM user_answer WHERE user_id = ? ORDER BY slot", Long.class, userId))
				.containsExactly(questions.get(1), questions.get(2), questions.get(0));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT answer FROM user_answer WHERE user_id = ? AND slot = 1", String.class, userId))
				.isEqualTo("질문 %d에 대한 열 글자 넘는 답".formatted(questions.get(1)));
		assertThat(jdbcTemplate.queryForObject("SELECT user_id FROM burrow WHERE id = ?", Long.class, burrowId))
				.isEqualTo(userId);
		Map<String, Object> entrance = jdbcTemplate.queryForMap(
				"SELECT term_id, campus_floor_id, slot FROM burrow_entrance WHERE burrow_id = ?", burrowId);
		assertThat(((Number) entrance.get("term_id")).longValue()).isEqualTo(steps.currentTermId());
		assertThat(((Number) entrance.get("campus_floor_id")).longValue()).isEqualTo(testUsers.seoulFloorId(2));
		assertThat(((Number) entrance.get("slot")).intValue()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT onboarded_at IS NOT NULL FROM `user` WHERE id = ?",
				Boolean.class, userId)).isTrue();
		assertThat(steps.holder("두더지왕")).isNull();

		mockMvc.perform(get("/api/v1/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nextStep").value("HOME"));
	}

	@Test
	void 닉네임을_확인하지_않았어도_아무도_쓰지_않으면_저장한다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);

		complete(login("mole@example.com"), steps.completeBody("두더지왕")).andExpect(status().isCreated());
	}

	@Test
	void 이미_마친_계정이_다시_부르면_본문을_보지_않고_200과_같은_땅굴을_준다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login("mole@example.com");
		String first = complete(session, steps.completeBody("두더지왕"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long burrowId = ((Number) JsonPath.read(first, "$.data.burrowId")).longValue();

		complete(session, """
				{ "nickname": "!", "interestIds": [1] }
				""")
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": { "userId": %d, "nextStep": "HOME", "burrowId": %d },
						  "error": null
						}
						""".formatted(userId, burrowId), JsonCompareMode.STRICT));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM burrow WHERE user_id = ?", Integer.class, userId))
				.isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT nickname FROM user_profile WHERE user_id = ?", String.class,
				userId)).isEqualTo("두더지왕");
	}

	@Test
	void 같은_들판에_두_번째로_확정한_땅굴은_다음_자리에_놓인다() throws Exception {
		testUsers.createEmailUser("mole@example.com", PASSWORD);
		long second = testUsers.createEmailUser("mole2@example.com", PASSWORD);
		complete(login("mole@example.com"), steps.completeBody("첫번째")).andExpect(status().isCreated());

		complete(login("mole2@example.com"), steps.completeBody("두번째")).andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForObject("""
				SELECT e.slot FROM burrow_entrance e JOIN burrow b ON b.id = e.burrow_id WHERE b.user_id = ?
				""", Integer.class, second)).isEqualTo(2);
	}

	@Test
	void 관심사가_2개이고_두_번째_답변이_짧으면_400과_어긋난_필드를_모두_준다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		List<Long> questions = steps.questionIds(3);

		complete(login("mole@example.com"), """
				{
				  "nickname": "두더지왕",
				  "mole": { "head": null, "face": null, "neck": null, "hand": null },
				  "interestIds": %s,
				  "answers": [
				    { "introQuestionId": %d, "answer": "운동하고 나면 무조건 제로 콜라를 마셔요" },
				    { "introQuestionId": %d, "answer": "짧아요" },
				    { "introQuestionId": %d, "answer": "출퇴근용으로 산 접이식 자전거가 최고예요" }
				  ]
				}
				""".formatted(steps.interestIds(2), questions.get(0), questions.get(1), questions.get(2)))
				.andExpect(status().isBadRequest())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": {
						    "code": "INVALID_INPUT",
						    "message": "입력값이 올바르지 않습니다.",
						    "fields": [
						      { "field": "answers[1].answer", "message": "10자 이상 50자 이하로 써야 합니다." },
						      { "field": "interestIds", "message": "3개 이상 10개 이하로 골라야 합니다." }
						    ],
						    "detail": null
						  }
						}
						""", JsonCompareMode.STRICT));
		assertNothingSaved(userId);
	}

	@Test
	void 닉네임_아이템_코드_관심사_겹침_질문_겹침이_어긋나면_400이다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		List<Long> interests = steps.interestIds(3);
		long question = steps.questionIds(1).getFirst();

		complete(login("mole@example.com"), """
				{
				  "nickname": "두더지 왕",
				  "mole": { "head": "cap-red", "face": null, "neck": null, "hand": null },
				  "interestIds": [%d, %d, %d],
				  "answers": [
				    { "introQuestionId": %d, "answer": "운동하고 나면 무조건 제로 콜라를 마셔요" },
				    { "introQuestionId": %d, "answer": "요즘은 인디 밴드 노래만 돌려 듣고 있어요" },
				    { "introQuestionId": %d, "answer": "출퇴근용으로 산 접이식 자전거가 최고예요" }
				  ]
				}
				""".formatted(interests.get(0), interests.get(1), interests.get(0), question, question, question))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(contains(
						"answers", "interestIds", "mole.head", "nickname")));
		assertNothingSaved(userId);
	}

	@Test
	void 지운_관심사를_고르면_404_INTEREST_NOT_FOUND다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		List<Long> interests = steps.interestIds(3);
		jdbcTemplate.update("UPDATE interest SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", interests.get(1));

		complete(login("mole@example.com"), steps.completeBody("두더지왕", interests, steps.questionIds(3)))
				.andExpect(status().isNotFound())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": { "code": "INTEREST_NOT_FOUND", "message": "없거나 지운 관심사입니다.", "fields": null, "detail": null }
						}
						""", JsonCompareMode.STRICT));
		assertNothingSaved(userId);
	}

	@Test
	void 없는_질문에_답하면_404_INTRO_QUESTION_NOT_FOUND다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		List<Long> questions = steps.questionIds(2);

		complete(login("mole@example.com"), steps.completeBody("두더지왕", steps.interestIds(3),
				List.of(questions.get(0), questions.get(1), 999_999L)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("INTRO_QUESTION_NOT_FOUND"));
		assertNothingSaved(userId);
	}

	@Test
	void 다른_계정이_맡아_둔_닉네임이면_409_NICKNAME_DUPLICATED이고_아무것도_저장하지_않는다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		testUsers.createEmailUser("other@example.com", PASSWORD);
		checkNickname(login("other@example.com"), "두더지왕").andExpect(status().isOk());

		complete(login("mole@example.com"), steps.completeBody("두더지왕"))
				.andExpect(status().isConflict())
				.andExpect(content().json("""
						{
						  "success": false,
						  "data": null,
						  "error": { "code": "NICKNAME_DUPLICATED", "message": "이미 사용 중인 닉네임입니다.", "fields": null, "detail": null }
						}
						""", JsonCompareMode.STRICT));
		assertNothingSaved(userId);
	}

	@Test
	void 확정은_저장하기_전에_닉네임을_맡아_다른_계정이_그사이_확인하지_못한다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		testUsers.createEmailUser("other@example.com", PASSWORD);
		Cookie session = login("mole@example.com");
		Cookie other = login("other@example.com");
		// 닉네임을 맡은 뒤 입구를 만들다 실패하게 해서, 확정이 저장 전에 닉네임을 맡는지 본다.
		jdbcTemplate.update("UPDATE term SET opened_at = '2099-01-01 00:00:00'");

		complete(session, steps.completeBody("두더지왕")).andExpect(status().isInternalServerError());

		assertThat(steps.holder("두더지왕")).isEqualTo(String.valueOf(userId));
		checkNickname(other, "두더지왕")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
	}

	@Test
	void 다른_사람이_대소문자만_다른_닉네임을_쓰고_있으면_409다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		long other = testUsers.createEmailUser("other@example.com", PASSWORD);
		jdbcTemplate.update("UPDATE user_profile SET nickname = 'Mole' WHERE user_id = ?", other);

		complete(login("mole@example.com"), steps.completeBody("mole"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
		assertNothingSaved(userId);
	}

	@Test
	void 열린_학기가_없으면_500_INTERNAL_ERROR이고_아무것도_저장하지_않는다() throws Exception {
		long userId = testUsers.createEmailUser("mole@example.com", PASSWORD);
		Cookie session = login("mole@example.com");
		jdbcTemplate.update("UPDATE term SET opened_at = '2099-01-01 00:00:00'");

		complete(session, steps.completeBody("두더지왕"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
		assertNothingSaved(userId);
	}

	@Test
	void 로그인하지_않았으면_401_UNAUTHENTICATED다() throws Exception {
		mockMvc.perform(post("/api/v1/me/onboarding/complete")
						.contentType(MediaType.APPLICATION_JSON)
						.content(steps.completeBody("두더지왕")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private void assertNothingSaved(long userId) {
		assertThat(jdbcTemplate.queryForObject("SELECT onboarded_at IS NULL FROM `user` WHERE id = ?",
				Boolean.class, userId)).isTrue();
		assertThat(jdbcTemplate.queryForObject("SELECT nickname FROM user_profile WHERE user_id = ?", String.class,
				userId)).isNull();
		for (String table : List.of("user_interest", "user_answer", "burrow")) {
			assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?",
					Integer.class, userId)).as(table).isZero();
		}
	}

	private ResultActions complete(Cookie session, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/me/onboarding/complete")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions checkNickname(Cookie session, String nickname) throws Exception {
		return mockMvc.perform(post("/api/v1/nicknames/check")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "nickname": "%s" }
						""".formatted(nickname)));
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
