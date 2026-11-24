package com.itting.burrow;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BurrowExploreApiTests {

	private static final String FIELDS = "/api/v1/terms/{termId}/fields";
	private static final String FIELD = FIELDS + "/{campusFloorId}";
	private static final String VISIT = "/api/v1/users/{userId}/burrow";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	TestUsers testUsers;
	@Autowired
	OnboardingSteps steps;
	@PersistenceContext
	EntityManager entityManager;

	private Members members;

	@BeforeEach
	void setUp() {
		steps.clearNicknameHolds();
		members = new Members(mockMvc, testUsers, steps, jdbc);
	}

	@Test
	void 층_목록의_순서를_유지하고_내_입구만_있는_층도_비어있지_않다() throws Exception {
		Member viewer = members.onboard("field-list@example.com", "들판목록");
		int deletedFloor = (int) testUsers.seoulFloorId(3);
		jdbc.update("UPDATE campus_floor SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deletedFloor);
		int otherCampusFloor = jdbc.queryForObject("""
				SELECT f.id FROM campus_floor f JOIN campus c ON c.id = f.campus_id WHERE c.name = '대전' AND f.floor = 1
				""", Integer.class);
		jdbc.update("UPDATE campus SET deleted_at = UTC_TIMESTAMP(6) WHERE name = '대전'");

		mockMvc.perform(get(FIELDS, steps.currentTermId()).cookie(viewer.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].campusFloorId").value(testUsers.seoulFloorId(1)))
				.andExpect(jsonPath("$.data.items[0].isEmpty").value(true))
				.andExpect(jsonPath("$.data.items[1].campusFloorId").value(testUsers.seoulFloorId(2)))
				.andExpect(jsonPath("$.data.items[1].isEmpty").value(false))
				.andExpect(jsonPath("$.data.items[2].campusFloorId").value(testUsers.seoulFloorId(4)))
				.andExpect(jsonPath("$.data.items[*].campusFloorId", not(hasItem(deletedFloor))))
				.andExpect(jsonPath("$.data.items[*].campusFloorId", not(hasItem(otherCampusFloor))));
	}

	@Test
	void 들판_상세는_자리순으로_내_입구와_정지된_계정의_방노트를_준다() throws Exception {
		List<Long> interests = steps.interestIds(5);
		List<Long> questions = steps.questionIds(4);
		Member viewer = members.onboard("field-viewer@example.com", "탐색두더지");
		Member owner = members.onboard("field-owner@example.com", "들판친구",
				List.of(interests.get(4), interests.get(1), interests.get(3)),
				List.of(questions.get(3), questions.get(0), questions.get(2)));
		Member withdrawn = members.onboard("field-withdrawn@example.com", "떠난두더지");
		testUsers.withdraw(withdrawn.userId());
		jdbc.update("UPDATE user_profile SET intro = ? WHERE user_id = ?", "들판에서 만나면 인사해요", owner.userId());
		suspend(owner);

		mockMvc.perform(get(FIELD, steps.currentTermId(), testUsers.seoulFloorId(2)).cookie(viewer.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.term.id").value(steps.currentTermId()))
				.andExpect(jsonPath("$.data.term.isCurrent").value(true))
				.andExpect(jsonPath("$.data.campusName").value("서울"))
				.andExpect(jsonPath("$.data.floor").value(2))
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andExpect(jsonPath("$.data.items[*].slot", contains(1, 2)))
				.andExpect(jsonPath("$.data.items[0].burrowId").value(viewer.burrowId()))
				.andExpect(jsonPath("$.data.items[0].isMine").value(true))
				.andExpect(jsonPath("$.data.items[0].intro").value(nullValue()))
				.andExpect(jsonPath("$.data.items[0].interests[*].isShared", contains(false, false, false)))
				.andExpect(jsonPath("$.data.items[1].burrowId").value(owner.burrowId()))
				.andExpect(jsonPath("$.data.items[1].isMine").value(false))
				.andExpect(jsonPath("$.data.items[1].owner.nickname").value("들판친구"))
				.andExpect(jsonPath("$.data.items[1].intro").value("들판에서 만나면 인사해요"))
				.andExpect(jsonPath("$.data.items[1].interests[*].isShared", contains(false, true, false)))
				.andExpect(jsonPath("$.data.items[1].answers[*].slot", contains(1, 2, 3)))
				.andExpect(jsonPath("$.data.items[1].answers[*].introQuestionId", contains(
						questions.get(3).intValue(), questions.get(0).intValue(), questions.get(2).intValue())));
	}

	@Test
	void 빈_들판은_빈_입구_배열을_준다() throws Exception {
		Member viewer = members.onboard("field-empty@example.com", "빈들판");
		mockMvc.perform(get(FIELD, steps.currentTermId(), testUsers.seoulFloorId(5)).cookie(viewer.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	@Test
	void 없거나_미래에_열리는_학기는_두_들판_API에서_404다() throws Exception {
		Member viewer = members.onboard("field-term@example.com", "학기탐색");
		jdbc.update("""
				INSERT INTO term (year, half, opened_at, created_at, updated_at)
				VALUES (2028, 1, UTC_TIMESTAMP(6) + INTERVAL 1 DAY, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""");
		long future = jdbc.queryForObject("SELECT id FROM term WHERE year = 2028 AND half = 1", Long.class);
		for (long termId : List.of(future, Long.MAX_VALUE)) {
			mockMvc.perform(get(FIELDS, termId).cookie(viewer.session()))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TERM_NOT_FOUND"));
			mockMvc.perform(get(FIELD, termId, testUsers.seoulFloorId(2)).cookie(viewer.session()))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("TERM_NOT_FOUND"));
		}
	}

	@Test
	void 없거나_삭제된_층의_들판은_404다() throws Exception {
		Member viewer = members.onboard("field-floor@example.com", "층탐색");
		long deletedFloor = testUsers.seoulFloorId(3);
		jdbc.update("UPDATE campus_floor SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deletedFloor);
		for (long floorId : List.of(deletedFloor, Long.MAX_VALUE)) {
			mockMvc.perform(get(FIELD, steps.currentTermId(), floorId).cookie(viewer.session()))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("CAMPUS_FLOOR_NOT_FOUND"));
		}
	}

	@Test
	void 지난_학기_정지_계정의_땅굴을_방문해_방노트와_공개_방명록_수를_받는다() throws Exception {
		Member viewer = members.onboard("visit-viewer@example.com", "방문두더지");
		Member owner = members.onboard("visit-owner@example.com", "지난학기");
		long oldTerm = steps.currentTermId();
		long oldFloor = testUsers.seoulFloorId(2);
		Instant now = Instant.now();
		members.writeGuestbook(owner.burrowId(), viewer.userId(), "공개 방명록", now);
		members.writeHiddenGuestbook(owner.burrowId(), viewer.userId(), "가려진 글", now);
		entityManager.flush();
		jdbc.update("UPDATE user_profile SET intro = ? WHERE user_id = ?", "지난 학기 방노트입니다", owner.userId());
		entityManager.clear();
		suspend(owner);
		steps.openNewTerm();
		mockMvc.perform(post("/api/v1/me/burrow/move").cookie(viewer.session()).contentType(MediaType.APPLICATION_JSON)
						.content("{\"campusFloorId\":%d}".formatted(testUsers.seoulFloorId(5))))
				.andExpect(status().isOk());

		mockMvc.perform(get(FIELD, oldTerm, oldFloor).cookie(viewer.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.term.isCurrent").value(false))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].burrowId").value(owner.burrowId()))
				.andExpect(jsonPath("$.data.items[0].slot").value(2));
		mockMvc.perform(get(VISIT, owner.userId()).cookie(viewer.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isMine").value(false))
				.andExpect(jsonPath("$.data.owner.id").value(owner.userId()))
				.andExpect(jsonPath("$.data.intro").value("지난 학기 방노트입니다"))
				.andExpect(jsonPath("$.data.interests[*].isShared", contains(true, true, true)))
				.andExpect(jsonPath("$.data.answers.length()").value(3))
				.andExpect(jsonPath("$.data.guestbookCount").value(1))
				.andExpect(jsonPath("$.data.entrance.termId").value(oldTerm))
				.andExpect(jsonPath("$.data.entrance.campusFloorId").value(oldFloor))
				.andExpect(jsonPath("$.data.isRegular").value(false))
				.andExpect(jsonPath("$.data.chat.status").value("NONE"))
				.andExpect(jsonPath("$.data.chat.chatRoomId").value(nullValue()));
	}

	@Test
	void 내_땅굴을_방문하면_isMine이_참이다() throws Exception {
		Member viewer = members.onboard("visit-self@example.com", "내땅굴방문");
		mockMvc.perform(get(VISIT, viewer.userId()).cookie(viewer.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isMine").value(true))
				.andExpect(jsonPath("$.data.isRegular").value(false))
				.andExpect(jsonPath("$.data.chat.status").value("NONE"))
				.andExpect(jsonPath("$.data.interests[*].isShared", contains(false, false, false)));
	}

	@Test
	void 미온보딩과_탈퇴_계정과_없는_계정의_땅굴은_404다() throws Exception {
		Member viewer = members.onboard("visit-missing@example.com", "방문계정");
		Member owner = members.onboard("visit-withdrawn@example.com", "탈퇴두더지");
		long pending = testUsers.createEmailUser("visit-pending@example.com", Members.PASSWORD);
		testUsers.withdraw(owner.userId());

		for (long userId : List.of(pending, owner.userId(), Long.MAX_VALUE)) {
			mockMvc.perform(get(VISIT, userId).cookie(viewer.session()))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("BURROW_NOT_FOUND"));
		}
	}

	@Test
	void 탐색과_방문은_로그인과_이번_학기_선택이_필요하다() throws Exception {
		Member viewer = members.onboard("explore-auth@example.com", "탐색권한");
		String pendingEmail = "explore-pending@example.com";
		testUsers.createEmailUser(pendingEmail, Members.PASSWORD);
		Cookie pending = members.login(pendingEmail);
		long termId = steps.openNewTerm();

		for (String path : List.of("/api/v1/terms/" + termId + "/fields",
				"/api/v1/terms/" + termId + "/fields/" + testUsers.seoulFloorId(2),
				"/api/v1/users/" + viewer.userId() + "/burrow")) {
			mockMvc.perform(get(path)).andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
			mockMvc.perform(get(path).cookie(pending)).andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
			mockMvc.perform(get(path).cookie(viewer.session())).andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code").value("NEW_TERM_INCOMPLETE"));
		}
	}

	@Test
	void API_문서에서_방문_대상과_이사_본문을_입력할_수_있다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/users/{userId}/burrow'].get.parameters[?(@.in == 'path')].name",
						contains("userId")))
				.andExpect(jsonPath("$.paths['/api/v1/me/burrow/move'].post.requestBody.content['application/json'].schema['$ref']")
						.value("#/components/schemas/BurrowMoveRequest"));
	}

	private void suspend(Member owner) {
		Instant now = Instant.now();
		testUsers.suspend(owner.userId(), testUsers.createAdmin(), "정지 기록", now.minusSeconds(60),
				now.plusSeconds(3600), null);
	}

}
