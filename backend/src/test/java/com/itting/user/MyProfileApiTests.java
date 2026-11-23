package com.itting.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.itting.support.Members;
import com.itting.support.Members.Member;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
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
 * {@code PATCH /api/v1/me/mole}, {@code PATCH /api/v1/me/profile}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MyProfileApiTests {

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

	Members members;

	@BeforeEach
	void setUp() {
		steps.clearNicknameHolds();
		members = new Members(mockMvc, testUsers, steps, jdbcTemplate);
	}

	// PATCH /api/v1/me/mole

	@Test
	void 두더지를_저장하면_200과_저장한_값을_주고_null인_부위는_비운다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");

		changeMole(mole.session(), """
				{ "head": "HAT_BLUE", "face": "GLASSES_ROUND", "neck": null, "hand": null }
				""")
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "success": true,
						  "data": { "head": "HAT_BLUE", "face": "GLASSES_ROUND", "neck": null, "hand": null },
						  "error": null
						}
						""", JsonCompareMode.STRICT));
		entityManager.flush();

		Map<String, Object> profile = jdbcTemplate.queryForMap(
				"SELECT mole_head, mole_face, mole_neck, mole_hand FROM user_profile WHERE user_id = ?", mole.userId());
		assertThat(profile).containsEntry("mole_head", "HAT_BLUE")
				.containsEntry("mole_face", "GLASSES_ROUND")
				.containsEntry("mole_neck", null)
				.containsEntry("mole_hand", null);
		mockMvc.perform(get("/api/v1/me/burrow").cookie(mole.session()))
				.andExpect(jsonPath("$.data.owner.mole.head").value("HAT_BLUE"))
				.andExpect(jsonPath("$.data.owner.mole.neck").isEmpty());
	}

	@Test
	void 두더지의_부위가_빠지거나_코드_모양이_틀리면_400과_fields를_모두_준다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");

		changeMole(mole.session(), """
				{ "head": "cap_red", "face": null, "neck": null }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(
						org.hamcrest.Matchers.containsInAnyOrder("hand", "head")));
		entityManager.flush();

		assertThat(jdbcTemplate.queryForObject("SELECT mole_head FROM user_profile WHERE user_id = ?", String.class,
				mole.userId())).isEqualTo("CAP_RED");
	}

	@Test
	void 두더지_저장은_로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(patch("/api/v1/me/mole")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "head": null, "face": null, "neck": null, "hand": null }
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	// PATCH /api/v1/me/profile

	@Test
	void 내_정보를_모두_바꾸면_200과_바뀐_내_땅굴을_주고_모두_저장한다() throws Exception {
		List<Long> interests = steps.interestIds(6);
		List<Long> questions = steps.questionIds(5);
		Member mole = members.onboard("mole@example.com", "두더지왕", interests.subList(0, 3), questions.subList(0, 3));
		mockMvc.perform(post("/api/v1/nicknames/check")
						.cookie(mole.session())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "nickname": "달리는두더지" }
								"""))
				.andExpect(status().isOk());
		long floor5 = testUsers.seoulFloorId(5);
		List<Long> newInterests = List.of(interests.get(4), interests.get(0), interests.get(3), interests.get(5));
		List<Long> newQuestions = List.of(questions.get(3), questions.get(1), questions.get(4));

		changeProfile(mole.session(), profileBody("  달리는두더지 ", floor5, newInterests, newQuestions,
				"\"  달리기와 음악을 좋아하는 15기입니다.  \""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.burrowId").value(mole.burrowId()))
				.andExpect(jsonPath("$.data.owner.nickname").value("달리는두더지"))
				.andExpect(jsonPath("$.data.owner.floor").value(5))
				.andExpect(jsonPath("$.data.owner.mole.head").value("CAP_RED"))
				.andExpect(jsonPath("$.data.campusFloorId").value(floor5))
				.andExpect(jsonPath("$.data.intro").value("달리기와 음악을 좋아하는 15기입니다."))
				.andExpect(jsonPath("$.data.interests[*].id").value(contains(newInterests.stream()
						.map(Long::intValue).toArray())))
				.andExpect(jsonPath("$.data.answers[*].slot").value(contains(1, 2, 3)))
				.andExpect(jsonPath("$.data.answers[*].introQuestionId").value(contains(newQuestions.stream()
						.map(Long::intValue).toArray())))
				.andExpect(jsonPath("$.data.answers[0].answer")
						.value("질문 %d에 대한 새로 쓴 답변입니다".formatted(questions.get(3))))
				.andExpect(jsonPath("$.data.entrance.termId").value(steps.currentTermId()))
				.andExpect(jsonPath("$.data.entrance.campusFloorId").value(floor5))
				.andExpect(jsonPath("$.data.entrance.slot").value(1));
		entityManager.flush();

		Map<String, Object> profile = jdbcTemplate.queryForMap(
				"SELECT nickname, campus_floor_id, intro, generation FROM user_profile WHERE user_id = ?",
				mole.userId());
		assertThat(profile).containsEntry("nickname", "달리는두더지")
				.containsEntry("intro", "달리기와 음악을 좋아하는 15기입니다.");
		assertThat(((Number) profile.get("campus_floor_id")).longValue()).isEqualTo(floor5);
		assertThat(((Number) profile.get("generation")).intValue()).isEqualTo(15);
		assertThat(jdbcTemplate.queryForList(
				"SELECT interest_id FROM user_interest WHERE user_id = ? ORDER BY sort_order", Long.class,
				mole.userId())).containsExactlyElementsOf(newInterests);
		assertThat(jdbcTemplate.queryForList(
				"SELECT sort_order FROM user_interest WHERE user_id = ? ORDER BY sort_order", Integer.class,
				mole.userId())).containsExactly(1, 2, 3, 4);
		assertThat(jdbcTemplate.queryForList(
				"SELECT intro_question_id FROM user_answer WHERE user_id = ? ORDER BY slot", Long.class,
				mole.userId())).containsExactlyElementsOf(newQuestions);
		assertThat(steps.holder("달리는두더지")).isNull();
	}

	@Test
	void 캠퍼스_층을_바꾸면_입구가_새_들판의_다음_자리로_옮겨지고_빈_자리는_다시_쓰지_않는다() throws Exception {
		Member first = members.onboard("first@example.com", "첫째두더지");
		Member second = members.onboard("second@example.com", "둘째두더지");
		members.onboard("third@example.com", "셋째두더지");
		long floor2 = testUsers.seoulFloorId(2);
		long floor5 = testUsers.seoulFloorId(5);

		changeProfile(first.session(), defaultBody("첫째두더지", floor5))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entrance.campusFloorId").value(floor5))
				.andExpect(jsonPath("$.data.entrance.slot").value(1));
		changeProfile(second.session(), defaultBody("둘째두더지", floor5))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entrance.slot").value(2));
		Member fourth = members.onboard("fourth@example.com", "넷째두더지");

		// 2층의 1 · 2번 자리가 비었지만, 가장 큰 번호(셋째의 3) + 1을 준다.
		mockMvc.perform(get("/api/v1/me/burrow").cookie(fourth.session()))
				.andExpect(jsonPath("$.data.entrance.campusFloorId").value(floor2))
				.andExpect(jsonPath("$.data.entrance.slot").value(4));
		entityManager.flush();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM burrow_entrance WHERE burrow_id = ?",
				Integer.class, first.burrowId())).isEqualTo(1);
	}

	@Test
	void 캠퍼스_층이_그대로면_입구도_그대로다() throws Exception {
		members.onboard("first@example.com", "첫째두더지");
		Member second = members.onboard("second@example.com", "둘째두더지");

		changeProfile(second.session(), defaultBody("둘째두더지", testUsers.seoulFloorId(2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.entrance.campusFloorId").value(testUsers.seoulFloorId(2)))
				.andExpect(jsonPath("$.data.entrance.slot").value(2));
	}

	@Test
	void 두_자리의_질문을_서로_바꿔도_저장한다() throws Exception {
		List<Long> questions = steps.questionIds(3);
		Member mole = members.onboard("mole@example.com", "두더지왕", steps.interestIds(3), questions);
		List<Long> swapped = List.of(questions.get(1), questions.get(0), questions.get(2));

		changeProfile(mole.session(), profileBody("두더지왕", testUsers.seoulFloorId(2), steps.interestIds(3),
				swapped, "null"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.answers[*].introQuestionId").value(contains(swapped.stream()
						.map(Long::intValue).toArray())));
		entityManager.flush();

		assertThat(jdbcTemplate.queryForList(
				"SELECT intro_question_id FROM user_answer WHERE user_id = ? ORDER BY slot", Long.class,
				mole.userId())).containsExactlyElementsOf(swapped);
	}

	@Test
	void 자기소개를_null이나_공백만_보내면_비운다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");
		long floor2 = testUsers.seoulFloorId(2);
		changeProfile(mole.session(), profileBody("두더지왕", floor2, steps.interestIds(3), steps.questionIds(3),
				"\"안녕하세요\""))
				.andExpect(jsonPath("$.data.intro").value("안녕하세요"));

		changeProfile(mole.session(), profileBody("두더지왕", floor2, steps.interestIds(3), steps.questionIds(3),
				"\"   \""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.intro").isEmpty());
		changeProfile(mole.session(), profileBody("두더지왕", floor2, steps.interestIds(3), steps.questionIds(3),
				"\"안녕하세요\""));
		changeProfile(mole.session(), profileBody("두더지왕", floor2, steps.interestIds(3), steps.questionIds(3),
				"null"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.intro").isEmpty());
		entityManager.flush();

		assertThat(jdbcTemplate.queryForObject("SELECT intro FROM user_profile WHERE user_id = ?", String.class,
				mole.userId())).isNull();
	}

	@Test
	void 키가_빠지거나_값이_규칙에_어긋나면_400과_fields를_모두_준다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");
		long question = steps.questionIds(1).getFirst();

		changeProfile(mole.session(), """
				{
				  "nickname": "두더지 왕",
				  "campusFloorId": %d,
				  "interestIds": [%d, %d],
				  "answers": [
				    { "introQuestionId": %d, "answer": "열 글자가 넘는 답변입니다" },
				    { "introQuestionId": %d, "answer": "열 글자가 넘는 답변입니다" },
				    { "introQuestionId": %d, "answer": "짧음" }
				  ]
				}
				""".formatted(testUsers.seoulFloorId(2), steps.interestIds(2).get(0), steps.interestIds(2).get(1),
				question, question, question))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(
						contains("answers", "answers[2].answer", "interestIds", "intro", "nickname")));
	}

	@Test
	void 자기소개가_150자를_넘으면_400이다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");

		changeProfile(mole.session(), profileBody("두더지왕", testUsers.seoulFloorId(2), steps.interestIds(3),
				steps.questionIds(3), "\"" + "가".repeat(151) + "\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("intro")));
	}

	@Test
	void 없는_캠퍼스_층이면_404다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");

		changeProfile(mole.session(), defaultBody("두더지왕", 999_999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("CAMPUS_FLOOR_NOT_FOUND"));
	}

	@Test
	void 지운_관심사가_있으면_404다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");
		List<Long> interests = steps.interestIds(4);
		entityManager.flush();
		jdbcTemplate.update("UPDATE interest SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", interests.get(3));
		entityManager.clear();

		changeProfile(mole.session(), profileBody("두더지왕", testUsers.seoulFloorId(2), interests,
				steps.questionIds(3), "null"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("INTEREST_NOT_FOUND"));
	}

	@Test
	void 없는_질문이면_404다() throws Exception {
		Member mole = members.onboard("mole@example.com", "두더지왕");
		List<Long> questions = steps.questionIds(2);

		changeProfile(mole.session(), profileBody("두더지왕", testUsers.seoulFloorId(2), steps.interestIds(3),
				List.of(questions.get(0), questions.get(1), 999_999L), "null"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("INTRO_QUESTION_NOT_FOUND"));
	}

	@Test
	void 다른_사람이_쓰는_닉네임이면_409이고_아무것도_바꾸지_않는다() throws Exception {
		members.onboard("other@example.com", "Mole");
		Member mole = members.onboard("mole@example.com", "두더지왕");

		changeProfile(mole.session(), profileBody("mole", testUsers.seoulFloorId(5), steps.interestIds(3),
				steps.questionIds(3), "\"바뀌면 안 되는 자기소개\""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
		entityManager.flush();

		Map<String, Object> profile = jdbcTemplate.queryForMap(
				"SELECT nickname, campus_floor_id, intro FROM user_profile WHERE user_id = ?", mole.userId());
		assertThat(profile).containsEntry("nickname", "두더지왕").containsEntry("intro", null);
		assertThat(((Number) profile.get("campus_floor_id")).longValue()).isEqualTo(testUsers.seoulFloorId(2));
	}

	@Test
	void 다른_사람이_맡아_둔_닉네임이면_409다() throws Exception {
		Member other = members.onboard("other@example.com", "다른두더지");
		Member mole = members.onboard("mole@example.com", "두더지왕");
		mockMvc.perform(post("/api/v1/nicknames/check")
						.cookie(other.session())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "nickname": "맡아둔이름" }
								"""))
				.andExpect(status().isOk());

		changeProfile(mole.session(), defaultBody("맡아둔이름", testUsers.seoulFloorId(2)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("NICKNAME_DUPLICATED"));
	}

	@Test
	void 내_정보_저장은_로그인하지_않으면_401이다() throws Exception {
		changeProfile(null, defaultBody("두더지왕", testUsers.seoulFloorId(2)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private String defaultBody(String nickname, long campusFloorId) {
		return profileBody(nickname, campusFloorId, steps.interestIds(3), steps.questionIds(3), "null");
	}

	/**
	 * @param intro JSON 값 그대로({@code "null"}, {@code "\"안녕\""})
	 */
	private String profileBody(String nickname, long campusFloorId, List<Long> interestIds, List<Long> questionIds,
			String intro) {
		String interests = interestIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
		String answers = questionIds.stream()
				.map(id -> "{ \"introQuestionId\": %d, \"answer\": \"질문 %d에 대한 새로 쓴 답변입니다\" }".formatted(id, id))
				.collect(Collectors.joining(", "));
		return """
				{
				  "nickname": "%s",
				  "campusFloorId": %d,
				  "interestIds": [%s],
				  "answers": [%s],
				  "intro": %s
				}
				""".formatted(nickname, campusFloorId, interests, answers, intro);
	}

	private ResultActions changeMole(Cookie session, String body) throws Exception {
		return mockMvc.perform(patch("/api/v1/me/mole")
				.cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions changeProfile(Cookie session, String body) throws Exception {
		var request = patch("/api/v1/me/profile")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body);
		if (session != null) {
			request.cookie(session);
		}
		return mockMvc.perform(request);
	}

}
