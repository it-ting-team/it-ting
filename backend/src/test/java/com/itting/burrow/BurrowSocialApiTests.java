package com.itting.burrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.itting.support.Members;
import com.itting.support.Members.Member;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BurrowSocialApiTests {

	private static final String LIST = "/api/v1/me/regular-burrows";
	private static final String REGULAR = "/api/v1/burrows/{burrowId}/regular";
	private static final String RECOMMENDATION = "/api/v1/me/daily-recommendation";
	private static final String GUESTBOOK = "/api/v1/burrows/{burrowId}/guestbook-entries";

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired TestUsers users;
	@Autowired OnboardingSteps steps;
	@PersistenceContext EntityManager entityManager;
	private Members members;

	@BeforeEach
	void setUp() {
		steps.clearNicknameHolds();
		members = new Members(mvc, users, steps, jdbc);
	}

	@Test
	void 단골_목록은_내가_담은_땅굴만_최근순으로_준다() throws Exception {
		Member viewer = onboard("단골요청");
		Member older = onboard("먼저담은");
		Member newer = onboard("나중담은");
		mvc.perform(get(LIST).cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0));
		mvc.perform(put(REGULAR, older.burrowId()).cookie(viewer.session())).andExpect(status().isOk());
		mvc.perform(put(REGULAR, newer.burrowId()).cookie(viewer.session())).andExpect(status().isOk());
		mvc.perform(put(REGULAR, newer.burrowId()).cookie(older.session())).andExpect(status().isOk());
		jdbc.update("UPDATE regular_burrow SET created_at = UTC_TIMESTAMP(6) - INTERVAL 1 DAY WHERE user_id = ? AND burrow_id = ?",
				viewer.userId(), older.burrowId());

		mvc.perform(get(LIST).cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].burrowId", contains((int) newer.burrowId(), (int) older.burrowId())))
				.andExpect(jsonPath("$.data.items[0].owner.id").value(newer.userId()));
	}

	@Test
	void 단골_담기와_해제를_반복해도_조회_결과와_단골_수가_일치한다() throws Exception {
		Member viewer = onboard("단골손님");
		Member owner = onboard("단골주인");
		for (int attempt = 0; attempt < 2; attempt++) {
			mvc.perform(put(REGULAR, owner.burrowId()).cookie(viewer.session())).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.isRegular").value(true));
		}
		mvc.perform(get("/api/v1/users/{userId}/burrow", owner.userId()).cookie(viewer.session()))
				.andExpect(jsonPath("$.data.isRegular").value(true));
		mvc.perform(get("/api/v1/me/burrow").cookie(owner.session()))
				.andExpect(jsonPath("$.data.regularCount").value(1));
		for (int attempt = 0; attempt < 2; attempt++) {
			mvc.perform(delete(REGULAR, owner.burrowId()).cookie(viewer.session())).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.isRegular").value(false));
		}
		mvc.perform(get("/api/v1/users/{userId}/burrow", owner.userId()).cookie(viewer.session()))
				.andExpect(jsonPath("$.data.isRegular").value(false));
		mvc.perform(get("/api/v1/me/burrow").cookie(owner.session()))
				.andExpect(jsonPath("$.data.regularCount").value(0));
	}

	@Test
	void 단골_API에서_로그인과_대상_땅굴을_검사한다() throws Exception {
		Member viewer = onboard("단골검증");
		mvc.perform(get(LIST)).andExpect(status().isUnauthorized());
		mvc.perform(put(REGULAR, viewer.burrowId()).cookie(viewer.session())).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("OWN_BURROW_NOT_ALLOWED"));
		mvc.perform(put(REGULAR, Long.MAX_VALUE).cookie(viewer.session())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("BURROW_NOT_FOUND"));
		mvc.perform(delete(REGULAR, Long.MAX_VALUE).cookie(viewer.session())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("BURROW_NOT_FOUND"));
	}

	@Test
	void 후보가_없어도_한국_날짜로_당일_수령이_기록되고_재요청은_본문을_무시한다() throws Exception {
		Member viewer = onboard("빈추천");
		mvc.perform(get(RECOMMENDATION).cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasReceived").value(false))
				.andExpect(jsonPath("$.data.recommendedOn").value(today().toString()));
		recommend(viewer, choice(users.seoulFloorId(2), 15)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.hasReceived").value(true))
				.andExpect(jsonPath("$.data.items.length()").value(0));
		recommend(viewer, "{").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasReceived").value(true))
				.andExpect(jsonPath("$.data.items.length()").value(0));
		mvc.perform(get(RECOMMENDATION).cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasReceived").value(true))
				.andExpect(jsonPath("$.data.items.length()").value(0));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_recommendation WHERE user_id = ?",
				Integer.class, viewer.userId())).isEqualTo(1);
	}

	@Test
	void 추천은_단골과_다른_층을_빼고_누적_횟수가_적은_친구를_먼저_고른다() throws Exception {
		Member viewer = onboard("추천요청");
		Member frequentlySeen = onboard("이미추천");
		Member friendOne = onboard("추천친구일");
		Member friendTwo = onboard("추천친구이");
		Member friendThree = onboard("추천친구삼");
		Member regular = onboard("제외단골");
		Member otherFloor = onboard("다른층친구");
		mvc.perform(put(REGULAR, regular.burrowId()).cookie(viewer.session())).andExpect(status().isOk());
		long floor = users.seoulFloorId(2);
		jdbc.update("UPDATE burrow_entrance SET campus_floor_id = ? WHERE burrow_id = ?",
				users.seoulFloorId(3), otherFloor.burrowId());
		jdbc.update("INSERT INTO daily_recommendation (user_id, recommended_on, created_at) VALUES (?, ?, UTC_TIMESTAMP(6))",
				viewer.userId(), today().minusDays(1));
		jdbc.update("""
				INSERT INTO daily_recommendation_member (daily_recommendation_id, sort_order, target_user_id)
				SELECT id, 1, ? FROM daily_recommendation WHERE user_id = ? AND recommended_on = ?
				""", frequentlySeen.userId(), viewer.userId(), today().minusDays(1));

		recommend(viewer, choice(floor, 15)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.items[*].owner.id", containsInAnyOrder(
						(int) friendOne.userId(), (int) friendTwo.userId(), (int) friendThree.userId())))
				.andExpect(jsonPath("$.data.items[0].answers.length()").value(3))
				.andExpect(jsonPath("$.data.items[0].interests[*].isShared", contains(true, true, true)));
		entityManager.flush();
		users.withdraw(friendOne.userId());
		mvc.perform(get(RECOMMENDATION).cookie(viewer.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].owner.id", containsInAnyOrder(
						(int) friendTwo.userId(), (int) friendThree.userId())));
	}

	@Test
	void 처음_추천받을_때는_기수와_층이_필수다() throws Exception {
		Member viewer = onboard("추천검증");
		mvc.perform(get(RECOMMENDATION)).andExpect(status().isUnauthorized());
		recommend(viewer, "{}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 방명록을_입력한_그대로_저장하고_주인에게_새_글_알림을_만든다() throws Exception {
		Member writer = onboard("방명록쓴이");
		Member owner = onboard("방명록주인");
		String content = " " + "🌱".repeat(48) + " ";
		write(writer, owner.burrowId(), content).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.content").value(content))
				.andExpect(jsonPath("$.data.author.id").value(writer.userId()))
				.andExpect(jsonPath("$.data.canDelete").value(true))
				.andExpect(jsonPath("$.data.canReport").value(false));
		mvc.perform(get(GUESTBOOK, owner.burrowId()).cookie(owner.session())).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].content").value(content));
		mvc.perform(get("/api/v1/me/burrow").cookie(owner.session()))
				.andExpect(jsonPath("$.data.guestbookCount").value(1));
		assertThat(guestbookNotifications(owner)).isEqualTo(1);
	}

	@Test
	void 방명록_알림을_끄면_글만_저장한다() throws Exception {
		Member writer = onboard("알림방문자");
		Member owner = onboard("알림끈주인");
		jdbc.update("""
				INSERT INTO notification_setting (user_id, burrow_on, created_at, updated_at)
				VALUES (?, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""", owner.userId());
		write(writer, owner.burrowId(), "안녕하세요").andExpect(status().isCreated());
		assertThat(guestbookNotifications(owner)).isZero();
	}

	@Test
	void 빈_글과_51자와_자기_땅굴_방명록_작성을_거부한다() throws Exception {
		Member writer = onboard("입력검증");
		Member owner = onboard("다른땅굴");
		write(writer, owner.burrowId(), "   ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		write(writer, owner.burrowId(), "🌱".repeat(51)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("content"));
		write(writer, writer.burrowId(), "안녕하세요").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("OWN_BURROW_NOT_ALLOWED"));
		write(writer, Long.MAX_VALUE, "안녕하세요").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("BURROW_NOT_FOUND"));
	}

	private Member onboard(String nickname) throws Exception {
		return members.onboard("kan47-" + Integer.toUnsignedString(nickname.hashCode()) + "@example.com", nickname);
	}

	private ResultActions recommend(Member viewer, String body) throws Exception {
		return mvc.perform(post(RECOMMENDATION).cookie(viewer.session())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions write(Member writer, long burrowId, String content) throws Exception {
		return mvc.perform(post(GUESTBOOK, burrowId).cookie(writer.session()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"%s\"}".formatted(content)));
	}

	private int guestbookNotifications(Member owner) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = 'GUESTBOOK_ENTRY_CREATED'",
				Integer.class, owner.userId());
	}

	private static String choice(long floor, int generation) {
		return "{\"campusFloorId\":%d,\"generation\":%d}".formatted(floor, generation);
	}

	private static LocalDate today() {
		return LocalDate.now(ZoneId.of("Asia/Seoul"));
	}

}
