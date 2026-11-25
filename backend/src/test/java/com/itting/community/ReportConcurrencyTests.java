package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.itting.community.domain.CommunityErrorCode;
import com.itting.community.domain.ReportReason;
import com.itting.community.domain.ReportTargetType;
import com.itting.community.dto.ReportCreateRequest;
import com.itting.community.service.ReportService;
import com.itting.global.error.BusinessException;
import com.itting.support.CommunitySteps;
import com.itting.support.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 신고가 동시에 들어와도 세 번째 신고에서 한 번만 가리고 알림도 한 번이며, 같은 사람의 겹친 신고는 하나만 받는다. 트랜잭션이 실제로
 * 겹쳐 커밋되어야 하므로 테스트 트랜잭션을 쓰지 않고, 만든 줄은 끝에 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReportConcurrencyTests {

	private static final int ROUNDS = 5;

	@Autowired
	ReportService reportService;

	@Autowired
	CommunitySteps steps;

	@Autowired
	JdbcTemplate jdbcTemplate;

	long author;

	List<Long> reporters;

	List<Long> posts = new ArrayList<>();

	@BeforeEach
	void setUp() {
		author = steps.createMember("report-race-author@example.com", "작성자");
		reporters = List.of(steps.createMember("report-race-1@example.com", "신고자1"),
				steps.createMember("report-race-2@example.com", "신고자2"),
				steps.createMember("report-race-3@example.com", "신고자3"));
	}

	@AfterEach
	void cleanUp() {
		List<Long> users = new ArrayList<>(reporters);
		users.add(author);
		for (long userId : users) {
			jdbcTemplate.update("DELETE FROM notification WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM report WHERE reporter_id = ?", userId);
		}
		for (long postId : posts) {
			jdbcTemplate.update("DELETE FROM post_version WHERE post_id = ?", postId);
			jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
		}
		for (long userId : users) {
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
	}

	@Test
	void 세_사람이_동시에_신고하면_글이_한_번_가려지고_알림도_한_번이다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			long postId = steps.insertPost(author, "FREE", "경합 글 " + round, "본문", Instant.now());
			posts.add(postId);

			List<Callable<Object>> tasks = new ArrayList<>();
			for (long reporter : reporters) {
				tasks.add(() -> reportService.report(reporter, request(ReportTargetType.POST, postId)));
			}
			List<Object> results = race(tasks);

			assertThat(results).noneMatch(result -> result instanceof Throwable);
			assertThat(jdbcTemplate.queryForObject("SELECT status FROM post WHERE id = ?", String.class, postId))
					.isEqualTo("HIDDEN");
			assertThat(jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = 'POST_HIDDEN' AND target_id = ?",
					Integer.class, author, postId)).isEqualTo(1);
		}
	}

	@Test
	void 같은_사람이_같은_사용자를_동시에_두_번_신고하면_하나만_받고_하나는_ALREADY_REPORTED다() throws Exception {
		long reporter = reporters.getFirst();
		for (int round = 0; round < ROUNDS; round++) {
			jdbcTemplate.update("DELETE FROM report WHERE reporter_id = ?", reporter);

			Callable<Object> task = () -> reportService.report(reporter, request(ReportTargetType.MEMBER, author));
			List<Object> results = race(List.of(task, task));

			assertThat(results).filteredOn(result -> !(result instanceof Throwable)).hasSize(1);
			assertThat(results).filteredOn(result -> result instanceof BusinessException e
					&& e.getErrorCode() == CommunityErrorCode.ALREADY_REPORTED).hasSize(1);
			assertThat(jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM report WHERE reporter_id = ? AND target_type = 'MEMBER' AND target_id = ?",
					Integer.class, reporter, author)).isEqualTo(1);
		}
	}

	private static ReportCreateRequest request(ReportTargetType targetType, long targetId) {
		return new ReportCreateRequest(targetType, targetId, ReportReason.SPAM, null);
	}

	/** 일들을 동시에 시작해 모두 끝날 때까지 기다린다. 결과나 던진 예외를 순서대로 준다. */
	private static List<Object> race(List<Callable<Object>> tasks) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		try {
			CyclicBarrier start = new CyclicBarrier(tasks.size());
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> task : tasks) {
				futures.add(pool.submit(() -> {
					start.await(10, TimeUnit.SECONDS);
					try {
						return task.call();
					}
					catch (Exception e) {
						return e;
					}
				}));
			}
			List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
