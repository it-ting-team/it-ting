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

import com.itting.community.service.CommentService;
import com.itting.community.service.PostReactionService;
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
 * 같은 사람이 좋아요 · 저장을 동시에 두 번 눌러도 오류가 나지 않고 줄이 하나이며 알림도 한 번이다. 두 트랜잭션이 실제로 겹쳐
 * 커밋되어야 하므로 테스트 트랜잭션을 쓰지 않고, 만든 줄은 끝에 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PostReactionConcurrencyTests {

	private static final int ROUNDS = 10;

	@Autowired
	PostReactionService postReactionService;

	@Autowired
	CommentService commentService;

	@Autowired
	CommunitySteps steps;

	@Autowired
	JdbcTemplate jdbcTemplate;

	long actor;

	long author;

	long postId;

	long commentId;

	@BeforeEach
	void setUp() {
		actor = steps.createMember("race-actor@example.com", "경주자");
		author = steps.createMember("race-author@example.com", "작성자");
		postId = steps.insertPost(author, "FREE", "경합 글", "본문", Instant.now());
		commentId = steps.comment(postId, author, null, "PUBLIC");
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM notification WHERE user_id IN (?, ?) OR actor_id IN (?, ?)", actor, author,
				actor, author);
		jdbcTemplate.update("DELETE FROM comment_like WHERE comment_id = ?", commentId);
		jdbcTemplate.update("DELETE FROM post_like WHERE post_id = ?", postId);
		jdbcTemplate.update("DELETE FROM post_bookmark WHERE post_id = ?", postId);
		jdbcTemplate.update("DELETE FROM comment_version WHERE comment_id = ?", commentId);
		jdbcTemplate.update("DELETE FROM comment WHERE post_id = ?", postId);
		jdbcTemplate.update("DELETE FROM post_version WHERE post_id = ?", postId);
		jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
		for (long userId : List.of(actor, author)) {
			jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
			jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
		}
	}

	@Test
	void 글_좋아요를_동시에_두_번_눌러도_오류가_없고_한_줄이고_알림은_한_번이다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			postReactionService.unlike(actor, postId);

			race(() -> postReactionService.like(actor, postId));

			assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ? AND user_id = ?",
					Integer.class, postId, actor)).isEqualTo(1);
		}
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = 'POST_LIKED'", Integer.class, author))
				.isEqualTo(1);
	}

	@Test
	void 글_저장을_동시에_두_번_눌러도_오류가_없고_한_줄이다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			postReactionService.unbookmark(actor, postId);

			race(() -> postReactionService.bookmark(actor, postId));

			assertThat(jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM post_bookmark WHERE post_id = ? AND user_id = ?", Integer.class, postId,
					actor)).isEqualTo(1);
		}
	}

	@Test
	void 댓글_좋아요를_동시에_두_번_눌러도_오류가_없고_한_줄이고_알림은_한_번이다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			commentService.unlike(actor, commentId);

			race(() -> commentService.like(actor, commentId));

			assertThat(jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM comment_like WHERE comment_id = ? AND user_id = ?", Integer.class, commentId,
					actor)).isEqualTo(1);
		}
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = 'COMMENT_LIKED'", Integer.class,
				author)).isEqualTo(1);
	}

	/** 같은 일을 두 스레드가 동시에 시작해 둘 다 끝날 때까지 기다린다. 어느 쪽이든 예외가 나면 테스트가 실패한다. */
	private static void race(Runnable task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CyclicBarrier start = new CyclicBarrier(2);
			List<Future<Void>> futures = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				Callable<Void> call = () -> {
					start.await(10, TimeUnit.SECONDS);
					task.run();
					return null;
				};
				futures.add(pool.submit(call));
			}
			for (Future<Void> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

}
