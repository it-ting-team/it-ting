package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.itting.support.CommunitySteps;
import com.itting.support.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
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
 * 댓글의 좋아요: {@code PUT · DELETE /api/v1/comments/{commentId}/like}. 알림은 {@link CommunityNotificationTests}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CommentLikeApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CommunitySteps steps;

	long me;

	long author;

	long third;

	/** 공개 글. 작성자는 author다. */
	long postId;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		me = steps.createMember("me@example.com", "나두더지");
		author = steps.createMember("author@example.com", "두더지왕");
		third = steps.createMember("third@example.com", "새벽산책");
		postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		session = steps.login(mockMvc, "me@example.com");
	}

	@Test
	void 좋아요를_누르면_누른_뒤의_수를_주고_다시_눌러도_그대로다() throws Exception {
		long comment = steps.comment(postId, third, null, "PUBLIC");
		steps.commentLike(comment, author);

		mockMvc.perform(put("/api/v1/comments/{id}/like", comment).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.isLiked").value(true))
				.andExpect(jsonPath("$.data.likeCount").value(2))
				.andExpect(jsonPath("$.error").isEmpty());
		mockMvc.perform(put("/api/v1/comments/{id}/like", comment).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isLiked").value(true))
				.andExpect(jsonPath("$.data.likeCount").value(2));

		assertThat(likes(comment, me)).isEqualTo(1);
		assertThat(likes(comment)).isEqualTo(2);
		// 시각은 UTC로 저장된다.
		assertThat(jdbcTemplate.queryForObject("""
				SELECT TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP(6)) FROM comment_like
				WHERE comment_id = ? AND user_id = ?
				""", Long.class, comment, me)).isBetween(-5L, 60L);
	}

	@Test
	void 답글에도_내_댓글에도_좋아요를_누를_수_있다() throws Exception {
		long top = steps.comment(postId, third, null, "PUBLIC");
		long reply = steps.comment(postId, author, top, "PUBLIC");
		long mine = steps.comment(postId, me, null, "PUBLIC");

		mockMvc.perform(put("/api/v1/comments/{id}/like", reply).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(1));
		mockMvc.perform(put("/api/v1/comments/{id}/like", mine).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(1));
	}

	@Test
	void 좋아요를_취소하면_수가_줄고_누르지_않았어도_성공이다() throws Exception {
		long comment = steps.comment(postId, third, null, "PUBLIC");
		steps.commentLike(comment, me);
		steps.commentLike(comment, author);

		mockMvc.perform(delete("/api/v1/comments/{id}/like", comment).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isLiked").value(false))
				.andExpect(jsonPath("$.data.likeCount").value(1));
		mockMvc.perform(delete("/api/v1/comments/{id}/like", comment).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isLiked").value(false))
				.andExpect(jsonPath("$.data.likeCount").value(1));

		assertThat(likes(comment, me)).isZero();
		assertThat(likes(comment, author)).isEqualTo(1);
	}

	@Test
	void 좋아요_취소는_댓글의_상태를_보지_않는다() throws Exception {
		long hidden = steps.comment(postId, third, null, "HIDDEN");
		long deleted = steps.comment(postId, third, null, "DELETED");
		steps.commentLike(hidden, me);
		steps.commentLike(deleted, me);

		mockMvc.perform(delete("/api/v1/comments/{id}/like", hidden).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(0));
		mockMvc.perform(delete("/api/v1/comments/{id}/like", deleted).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(0));
		mockMvc.perform(delete("/api/v1/comments/{id}/like", 999_999).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isLiked").value(false))
				.andExpect(jsonPath("$.data.likeCount").value(0));

		assertThat(likes(hidden, me)).isZero();
		assertThat(likes(deleted, me)).isZero();
	}

	@Test
	void 가려지거나_삭제된_댓글과_없는_댓글에는_좋아요를_누를_수_없다() throws Exception {
		long hidden = steps.comment(postId, third, null, "HIDDEN");
		long deleted = steps.comment(postId, third, null, "DELETED");

		for (long target : new long[] { hidden, deleted, 999_999L }) {
			mockMvc.perform(put("/api/v1/comments/{id}/like", target).cookie(session))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
		}

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment_like WHERE user_id = ?",
				Integer.class, me)).isZero();
	}

	@Test
	void 가려지거나_삭제된_글의_댓글에는_좋아요를_누를_수_없다() throws Exception {
		long hiddenPost = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());
		long deletedPost = steps.insertPost(author, "FREE", "DELETED", "지운 글", "본문", Instant.now());
		long onHiddenPost = steps.comment(hiddenPost, third, null, "PUBLIC");
		long replyOnHiddenPost = steps.comment(hiddenPost, third, onHiddenPost, "PUBLIC");
		long onDeletedPost = steps.comment(deletedPost, third, null, "PUBLIC");

		for (long target : new long[] { onHiddenPost, replyOnHiddenPost, onDeletedPost }) {
			mockMvc.perform(put("/api/v1/comments/{id}/like", target).cookie(session))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
		}

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment_like WHERE user_id = ?",
				Integer.class, me)).isZero();
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(put("/api/v1/comments/1/like"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(delete("/api/v1/comments/1/like")).andExpect(status().isUnauthorized());
	}

	private int likes(long commentId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment_like WHERE comment_id = ?", Integer.class,
				commentId);
	}

	private int likes(long commentId, long userId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM comment_like WHERE comment_id = ? AND user_id = ?", Integer.class, commentId,
				userId);
	}

}
