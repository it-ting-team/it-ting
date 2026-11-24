package com.itting.burrow;

import static org.assertj.core.api.Assertions.assertThat;
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
class BurrowMoveApiTests {

	private static final String MOVE = "/api/v1/me/burrow/move";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	TestUsers testUsers;
	@Autowired
	OnboardingSteps steps;

	private Members members;

	@BeforeEach
	void setUp() {
		steps.clearNicknameHolds();
		members = new Members(mockMvc, testUsers, steps, jdbc);
	}

	@Test
	void 이번_학기의_새_자리로_옮기고_HOME이_되며_기존_땅굴은_유지된다() throws Exception {
		long floor = testUsers.seoulFloorId(2);
		long oldTerm = steps.currentTermId();
		Member mover = members.onboard("move-user@example.com", "학기이사");
		long entranceId = jdbc.queryForObject("SELECT id FROM burrow_entrance WHERE burrow_id = ?", Long.class,
				mover.burrowId());
		long currentTerm = steps.openNewTerm();
		Member neighbor = members.onboard("move-neighbor@example.com", "새학기친구");
		members.writeGuestbook(mover.burrowId(), neighbor.userId(), "이사 가도 남아요", Instant.now());

		mockMvc.perform(get("/api/v1/me").cookie(mover.session()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("NEW_TERM"));
		move(mover, "{\"campusFloorId\":%d}".formatted(floor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(mover.userId()))
				.andExpect(jsonPath("$.data.nextStep").value("HOME"))
				.andExpect(jsonPath("$.data.entrance.termId").value(currentTerm))
				.andExpect(jsonPath("$.data.entrance.campusFloorId").value(floor))
				.andExpect(jsonPath("$.data.entrance.slot").value(2));
		mockMvc.perform(get("/api/v1/me").cookie(mover.session()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("HOME"));
		mockMvc.perform(get("/api/v1/me/burrow").cookie(mover.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.burrowId").value(mover.burrowId()))
				.andExpect(jsonPath("$.data.owner.mole.head").value("CAP_RED"))
				.andExpect(jsonPath("$.data.guestbookCount").value(1));
		assertThat(jdbc.queryForObject("SELECT term_id FROM burrow_entrance WHERE id = ?", Long.class, entranceId))
				.isEqualTo(currentTerm).isNotEqualTo(oldTerm);
	}

	@Test
	void 이사한_뒤에는_본문이_없거나_잘못돼도_현재_값을_반환한다() throws Exception {
		Member mover = members.onboard("move-repeat@example.com", "다시이사");
		long currentTerm = steps.openNewTerm();
		long destination = testUsers.seoulFloorId(5);
		move(mover, "{\"campusFloorId\":%d}".formatted(destination)).andExpect(status().isOk());

		for (String body : List.of("", "{", "{\"campusFloorId\":%d}".formatted(testUsers.seoulFloorId(6)))) {
			move(mover, body).andExpect(status().isOk())
					.andExpect(jsonPath("$.data.entrance.termId").value(currentTerm))
					.andExpect(jsonPath("$.data.entrance.campusFloorId").value(destination))
					.andExpect(jsonPath("$.data.entrance.slot").value(1));
		}
		assertThat(profileFloor(mover)).isEqualTo(destination);
	}

	@Test
	void 최초_이사의_본문이_없거나_JSON_객체가_아니면_400이다() throws Exception {
		Member mover = members.onboard("move-body@example.com", "이사본문");
		steps.openNewTerm();
		for (String body : List.of("", "{", "[]", "null", "{} {}")) {
			move(mover, body).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		}
	}

	@Test
	void 최초_이사의_층_id가_빠지거나_정수가_아니면_fields를_준다() throws Exception {
		Member mover = members.onboard("move-field@example.com", "이사검증");
		steps.openNewTerm();
		for (String body : List.of("{}", "{\"campusFloorId\":null}", "{\"campusFloorId\":\"2\"}",
				"{\"campusFloorId\":2.5}", "{\"campusFloorId\":9223372036854775808}")) {
			move(mover, body).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
					.andExpect(jsonPath("$.error.fields[0].field").value("campusFloorId"));
		}
	}

	@Test
	void 없거나_삭제된_층을_고르면_입구와_프로필을_유지한다() throws Exception {
		Member mover = members.onboard("move-invalid@example.com", "이사실패");
		long oldTerm = steps.currentTermId();
		long oldFloor = testUsers.seoulFloorId(2);
		long deletedFloor = testUsers.seoulFloorId(3);
		steps.openNewTerm();
		jdbc.update("UPDATE campus_floor SET deleted_at = UTC_TIMESTAMP(6) WHERE id = ?", deletedFloor);

		for (long floor : List.of(deletedFloor, Long.MAX_VALUE)) {
			move(mover, "{\"campusFloorId\":%d}".formatted(floor)).andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("CAMPUS_FLOOR_NOT_FOUND"));
		}
		assertThat(profileFloor(mover)).isEqualTo(oldFloor);
		assertThat(jdbc.queryForMap("SELECT term_id, campus_floor_id, slot FROM burrow_entrance WHERE burrow_id = ?",
				mover.burrowId())).containsEntry("term_id", oldTerm).containsEntry("campus_floor_id", oldFloor)
				.containsEntry("slot", 1);
	}

	@Test
	void 이사는_로그인과_온보딩을_마쳐야_할_수_있다() throws Exception {
		String email = "move-pending@example.com";
		testUsers.createEmailUser(email, Members.PASSWORD);

		mockMvc.perform(post(MOVE)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(post(MOVE).cookie(members.login(email))).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	private ResultActions move(Member mover, String body) throws Exception {
		return mockMvc.perform(post(MOVE).cookie(mover.session()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private Long profileFloor(Member mover) {
		return jdbc.queryForObject("SELECT campus_floor_id FROM user_profile WHERE user_id = ?", Long.class, mover.userId());
	}

}
