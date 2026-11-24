package com.itting.burrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.itting.support.Members;
import com.itting.support.Members.Member;
import com.itting.support.OnboardingSteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BurrowSocialConcurrencyTests {

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired TestUsers users;
	@Autowired OnboardingSteps steps;
	private Members members;
	private final List<Member> fixtures = new ArrayList<>();

	@BeforeEach
	void setUp() {
		steps.clearNicknameHolds();
		members = new Members(mvc, users, steps, jdbc);
	}

	@AfterEach
	void cleanUp() {
		for (Member member : fixtures) {
			jdbc.update("""
					DELETE m FROM daily_recommendation_member m JOIN daily_recommendation d ON d.id = m.daily_recommendation_id
					WHERE d.user_id = ? OR m.target_user_id = ?
					""", member.userId(), member.userId());
			jdbc.update("DELETE FROM daily_recommendation WHERE user_id = ?", member.userId());
			jdbc.update("DELETE FROM regular_burrow WHERE user_id = ? OR burrow_id = ?", member.userId(), member.burrowId());
			jdbc.update("DELETE FROM burrow_entrance WHERE burrow_id = ?", member.burrowId());
			for (String table : List.of("burrow", "user_answer", "user_interest", "user_profile")) {
				jdbc.update("DELETE FROM " + table + " WHERE user_id = ?", member.userId());
			}
			jdbc.update("DELETE FROM `user` WHERE id = ?", member.userId());
		}
	}

	@Test
	void 동시에_추천을_받아도_한_번만_확정한다() throws Exception {
		Member viewer = onboard(0);
		Member owner = onboard(1);
		long floor = users.seoulFloorId(2);
		Callable<MockHttpServletResponse> request = () -> recommend(viewer, floor, 15);
		List<MockHttpServletResponse> responses = together(request, request);

		assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 200);
		assertThat(responses.getFirst().getContentAsString()).isEqualTo(responses.getLast().getContentAsString());
		assertThat(jdbc.queryForList("""
				SELECT m.target_user_id FROM daily_recommendation_member m
				JOIN daily_recommendation d ON d.id = m.daily_recommendation_id WHERE d.user_id = ?
				""", Long.class, viewer.userId())).containsExactly(owner.userId());
	}

	@Test
	void 서로를_추천받는_두_계정이_동시에_확정할_수_있다() throws Exception {
		Member first = onboard(0);
		Member second = onboard(1);
		long floor = users.seoulFloorId(2);
		List<MockHttpServletResponse> responses = together(() -> recommend(first, floor, 15),
				() -> recommend(second, floor, 15));
		assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactly(201, 201);
		assertThat(jdbc.queryForList("""
				SELECT m.target_user_id FROM daily_recommendation_member m
				JOIN daily_recommendation d ON d.id = m.daily_recommendation_id
				WHERE d.user_id IN (?, ?) ORDER BY d.user_id
				""", Long.class, first.userId(), second.userId())).containsExactly(second.userId(), first.userId());
	}

	@Test
	void 없는_층과_잘못된_기수는_당일_기록을_남기지_않는다() throws Exception {
		Member viewer = onboard(0);
		long floor = users.seoulFloorId(2);
		MockHttpServletResponse invalid = recommend(viewer, floor, -1);
		assertThat(invalid.getStatus()).isEqualTo(400);
		assertThat(invalid.getContentAsString()).contains("INVALID_INPUT", "generation");
		MockHttpServletResponse missing = recommend(viewer, Long.MAX_VALUE, 15);
		assertThat(missing.getStatus()).isEqualTo(404);
		assertThat(missing.getContentAsString()).contains("CAMPUS_FLOOR_NOT_FOUND");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_recommendation WHERE user_id = ?",
				Integer.class, viewer.userId())).isZero();
		assertThat(recommend(viewer, floor, 15).getStatus()).isEqualTo(201);
	}

	@Test
	void 동시에_같은_단골을_담아도_한_줄만_저장한다() throws Exception {
		Member viewer = onboard(0);
		Member owner = onboard(1);
		Callable<MockHttpServletResponse> request = () -> mvc.perform(put("/api/v1/burrows/{burrowId}/regular",
				owner.burrowId()).cookie(viewer.session())).andReturn().getResponse();
		List<MockHttpServletResponse> responses = together(request, request);

		assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsExactly(200, 200);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM regular_burrow WHERE user_id = ? AND burrow_id = ?",
				Integer.class, viewer.userId(), owner.burrowId())).isEqualTo(1);
	}

	private Member onboard(int index) throws Exception {
		Member member = members.onboard("kan47-concurrent-" + index + "@example.com", "동시탐색" + index);
		fixtures.add(member);
		return member;
	}

	private MockHttpServletResponse recommend(Member viewer, long floor, int generation) throws Exception {
		return mvc.perform(post("/api/v1/me/daily-recommendation").cookie(viewer.session())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"campusFloorId\":%d,\"generation\":%d}".formatted(floor, generation)))
				.andReturn().getResponse();
	}

	private static <T> List<T> together(Callable<T> first, Callable<T> second) throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var firstResult = executor.submit(() -> afterStart(first, ready, start));
			var secondResult = executor.submit(() -> afterStart(second, ready, start));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(firstResult.get(30, TimeUnit.SECONDS), secondResult.get(30, TimeUnit.SECONDS));
		}
		finally {
			executor.shutdownNow();
		}
	}

	private static <T> T afterStart(Callable<T> task, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		start.await();
		return task.call();
	}

}
