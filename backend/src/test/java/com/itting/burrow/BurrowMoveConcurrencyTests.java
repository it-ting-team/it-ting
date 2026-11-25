package com.itting.burrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.itting.support.Members;
import com.itting.support.Members.Member;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 커밋과 잠금이 필요한 이사 동시성 테스트. 만든 행만 테스트 뒤에 정리한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BurrowMoveConcurrencyTests {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	TestUsers testUsers;
	@Autowired
	OnboardingSteps steps;

	private final List<Member> movers = new ArrayList<>();
	private Members members;
	private Long termId;

	@BeforeEach
	void setUp() {
		steps.clearNicknameHolds();
		members = new Members(mockMvc, testUsers, steps, jdbc);
	}

	@AfterEach
	void cleanUp() {
		for (Member mover : movers) {
			jdbc.update("DELETE FROM burrow_entrance WHERE burrow_id = ?", mover.burrowId());
			for (String table : List.of("burrow", "user_answer", "user_interest", "user_profile")) {
				jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", mover.userId());
			}
			jdbc.update("DELETE FROM `user` WHERE id = ?", mover.userId());
		}
		if (termId != null) {
			jdbc.update("DELETE FROM term WHERE id = ?", termId);
		}
	}

	@Test
	void 같은_계정이_서로_다른_층을_동시에_골라도_한_번만_옮긴다() throws Exception {
		Member mover = onboard(0);
		termId = steps.openNewTerm();
		long firstFloor = testUsers.seoulFloorId(4);
		long secondFloor = testUsers.seoulFloorId(5);
		List<String> responses = moveTogether(List.of(new Move(mover, firstFloor), new Move(mover, secondFloor)));

		assertThat(responses.getFirst()).isEqualTo(responses.getLast());
		Number acceptedFloor = JsonPath.read(responses.getFirst(), "$.data.entrance.campusFloorId");
		assertThat(acceptedFloor.longValue()).isIn(firstFloor, secondFloor);
		assertMoved(mover, acceptedFloor.longValue());
	}

	@Test
	void 여러_계정이_같은_목적지를_동시에_골라도_자리_번호가_겹치지_않는다() throws Exception {
		for (int i = 0; i < 4; i++) {
			onboard(i);
		}
		termId = steps.openNewTerm();
		long destination = testUsers.seoulFloorId(4);
		moveTogether(movers.stream().map(mover -> new Move(mover, destination)).toList());

		for (Member mover : movers) {
			assertMoved(mover, destination);
		}
		assertThat(jdbc.queryForList("""
				SELECT slot FROM burrow_entrance WHERE term_id = ? AND campus_floor_id = ? ORDER BY slot
				""", Integer.class, termId, destination)).containsExactly(1, 2, 3, 4);
	}

	private Member onboard(int index) throws Exception {
		Member mover = members.onboard("mover-concurrent-" + index + "@example.com", "동시학기이사" + index);
		movers.add(mover);
		return mover;
	}

	private void assertMoved(Member mover, long destination) {
		assertThat(jdbc.queryForMap("SELECT term_id, campus_floor_id FROM burrow_entrance WHERE burrow_id = ?",
				mover.burrowId())).containsEntry("term_id", termId).containsEntry("campus_floor_id", destination);
		assertThat(jdbc.queryForObject("SELECT campus_floor_id FROM user_profile WHERE user_id = ?", Long.class,
				mover.userId())).isEqualTo(destination);
	}

	private List<String> moveTogether(List<Move> moves) throws Exception {
		CountDownLatch ready = new CountDownLatch(moves.size());
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(moves.size());
		try {
			List<Future<String>> results = new ArrayList<>();
			for (Move move : moves) {
				results.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return mockMvc.perform(post("/api/v1/me/burrow/move").cookie(move.mover().session())
									.contentType(MediaType.APPLICATION_JSON)
									.content("{\"campusFloorId\":%d}".formatted(move.campusFloorId())))
							.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			List<String> responses = new ArrayList<>();
			for (Future<String> result : results) {
				responses.add(result.get(30, TimeUnit.SECONDS));
			}
			return responses;
		}
		finally {
			executor.shutdownNow();
		}
	}

	private record Move(Member mover, long campusFloorId) {
	}

}
