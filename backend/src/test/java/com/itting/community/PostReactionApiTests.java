package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.itting.support.CommunitySteps;
import com.itting.support.TestUsers;
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
 * 글의 좋아요와 저장: {@code PUT · DELETE /api/v1/posts/{postId}/like}, {@code PUT · DELETE /api/v1/posts/{postId}/bookmark}.
 * 알림은 {@link CommunityNotificationTests}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PostReactionApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CommunitySteps steps;

	@Autowired
	TestUsers testUsers;

	long me;

	long author;

	long third;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		me = steps.createMember("me@example.com", "나두더지");
		author = steps.createMember("author@example.com", "두더지왕");
		third = steps.createMember("third@example.com", "새벽산책");
		session = steps.login(mockMvc, "me@example.com");
	}

	@Test
	void 좋아요를_누르면_누른_뒤의_수를_주고_다시_눌러도_그대로다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		steps.like(postId, third, Instant.now());

		mockMvc.perform(put("/api/v1/posts/{id}/like", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.isLiked").value(true))
				.andExpect(jsonPath("$.data.likeCount").value(2))
				.andExpect(jsonPath("$.error").isEmpty());
		mockMvc.perform(put("/api/v1/posts/{id}/like", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isLiked").value(true))
				.andExpect(jsonPath("$.data.likeCount").value(2));

		assertThat(likes(postId, me)).isEqualTo(1);
		assertThat(likes(postId)).isEqualTo(2);
		// 시각은 UTC로 저장된다(인기 점수가 최근 7일의 UTC 시각으로 센다).
		assertThat(secondsSince("post_like", "post_id", postId, me)).isBetween(-5L, 60L);
	}

	@Test
	void 내_글에도_좋아요를_누를_수_있다() throws Exception {
		long mine = steps.insertPost(me, "FREE", "내 글", "본문", Instant.now());

		mockMvc.perform(put("/api/v1/posts/{id}/like", mine).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(1));
	}

	@Test
	void 좋아요를_취소하면_수가_줄고_누르지_않았어도_성공이다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		steps.like(postId, me, Instant.now());
		steps.like(postId, third, Instant.now());

		mockMvc.perform(delete("/api/v1/posts/{id}/like", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isLiked").value(false))
				.andExpect(jsonPath("$.data.likeCount").value(1));
		mockMvc.perform(delete("/api/v1/posts/{id}/like", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isLiked").value(false))
				.andExpect(jsonPath("$.data.likeCount").value(1));

		assertThat(likes(postId, me)).isZero();
		assertThat(likes(postId, third)).isEqualTo(1);
	}

	@Test
	void 좋아요_취소는_글의_상태를_보지_않는다() throws Exception {
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());
		long deleted = steps.insertPost(author, "FREE", "DELETED", "지운 글", "본문", Instant.now());
		steps.like(hidden, me, Instant.now());
		steps.like(deleted, me, Instant.now());

		mockMvc.perform(delete("/api/v1/posts/{id}/like", hidden).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(0));
		mockMvc.perform(delete("/api/v1/posts/{id}/like", deleted).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(0));
		mockMvc.perform(delete("/api/v1/posts/{id}/like", 999_999).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isLiked").value(false))
				.andExpect(jsonPath("$.data.likeCount").value(0));

		assertThat(likes(hidden, me)).isZero();
		assertThat(likes(deleted, me)).isZero();
	}

	@Test
	void 가려진_글_삭제된_글_없는_글에는_좋아요를_누를_수_없다() throws Exception {
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());
		long deleted = steps.insertPost(author, "FREE", "DELETED", "지운 글", "본문", Instant.now());
		long myHidden = steps.insertPost(me, "FREE", "HIDDEN", "가려진 내 글", "본문", Instant.now());

		mockMvc.perform(put("/api/v1/posts/{id}/like", hidden).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("POST_HIDDEN"));
		// 작성자가 불러도 같다.
		mockMvc.perform(put("/api/v1/posts/{id}/like", myHidden).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_HIDDEN"));
		mockMvc.perform(put("/api/v1/posts/{id}/like", deleted).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
		mockMvc.perform(put("/api/v1/posts/{id}/like", 999_999).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE user_id = ?", Integer.class,
				me)).isZero();
	}

	@Test
	void 글을_저장하면_저장되고_다시_저장해도_한_줄이다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());

		mockMvc.perform(put("/api/v1/posts/{id}/bookmark", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.isBookmarked").value(true))
				.andExpect(jsonPath("$.error").isEmpty());
		mockMvc.perform(put("/api/v1/posts/{id}/bookmark", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isBookmarked").value(true));

		assertThat(bookmarks(postId, me)).isEqualTo(1);
		assertThat(secondsSince("post_bookmark", "post_id", postId, me)).isBetween(-5L, 60L);
		// 글 상세가 저장한 글로 보여 준다.
		mockMvc.perform(get("/api/v1/posts/{id}", postId).cookie(session))
				.andExpect(jsonPath("$.data.isBookmarked").value(true));
	}

	@Test
	void 내_글도_저장할_수_있고_알림은_만들지_않는다() throws Exception {
		long mine = steps.insertPost(me, "FREE", "내 글", "본문", Instant.now());
		long others = steps.insertPost(author, "FREE", "남의 글", "본문", Instant.now());

		mockMvc.perform(put("/api/v1/posts/{id}/bookmark", mine).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isBookmarked").value(true));
		mockMvc.perform(put("/api/v1/posts/{id}/bookmark", others).cookie(session))
				.andExpect(status().isOk());

		assertThat(bookmarks(mine, me)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id IN (?, ?, ?)",
				Integer.class, me, author, third)).isZero();
	}

	@Test
	void 저장을_취소하면_지워지고_저장하지_않았어도_성공이다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		steps.bookmark(postId, me);
		steps.bookmark(postId, third);

		mockMvc.perform(delete("/api/v1/posts/{id}/bookmark", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isBookmarked").value(false));
		mockMvc.perform(delete("/api/v1/posts/{id}/bookmark", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isBookmarked").value(false));

		assertThat(bookmarks(postId, me)).isZero();
		assertThat(bookmarks(postId, third)).isEqualTo(1);
	}

	@Test
	void 저장_취소는_글의_상태를_보지_않는다() throws Exception {
		long deleted = steps.insertPost(author, "FREE", "DELETED", "지운 글", "본문", Instant.now());
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());
		steps.bookmark(deleted, me);
		steps.bookmark(hidden, me);

		mockMvc.perform(delete("/api/v1/posts/{id}/bookmark", deleted).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isBookmarked").value(false));
		mockMvc.perform(delete("/api/v1/posts/{id}/bookmark", hidden).cookie(session))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/posts/{id}/bookmark", 999_999).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isBookmarked").value(false));

		assertThat(bookmarks(deleted, me)).isZero();
		assertThat(bookmarks(hidden, me)).isZero();
	}

	@Test
	void 삭제된_글_가려진_글_없는_글은_저장할_수_없다() throws Exception {
		long deleted = steps.insertPost(author, "FREE", "DELETED", "지운 글", "본문", Instant.now());
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());
		long myHidden = steps.insertPost(me, "FREE", "HIDDEN", "가려진 내 글", "본문", Instant.now());

		mockMvc.perform(put("/api/v1/posts/{id}/bookmark", deleted).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
		mockMvc.perform(put("/api/v1/posts/{id}/bookmark", hidden).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_HIDDEN"));
		mockMvc.perform(put("/api/v1/posts/{id}/bookmark", myHidden).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_HIDDEN"));
		mockMvc.perform(put("/api/v1/posts/{id}/bookmark", 999_999).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_bookmark WHERE user_id = ?",
				Integer.class, me)).isZero();
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(put("/api/v1/posts/1/like"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(delete("/api/v1/posts/1/like")).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/v1/posts/1/bookmark")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/posts/1/bookmark")).andExpect(status().isUnauthorized());
	}

	@Test
	void 온보딩을_마치지_않은_계정은_403이다() throws Exception {
		testUsers.createEmailUser("fresh@example.com", CommunitySteps.PASSWORD);
		Cookie fresh = steps.login(mockMvc, "fresh@example.com");

		mockMvc.perform(put("/api/v1/posts/1/like").cookie(fresh))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
		mockMvc.perform(put("/api/v1/posts/1/bookmark").cookie(fresh))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	/** 그 줄의 {@code created_at}이 지금(UTC)보다 몇 초 전인지. */
	private long secondsSince(String table, String column, long id, long userId) {
		return jdbcTemplate.queryForObject("SELECT TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP(6)) FROM " + table
				+ " WHERE " + column + " = ? AND user_id = ?", Long.class, id, userId);
	}

	private int likes(long postId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ?", Integer.class,
				postId);
	}

	private int likes(long postId, long userId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ? AND user_id = ?",
				Integer.class, postId, userId);
	}

	private int bookmarks(long postId, long userId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_bookmark WHERE post_id = ? AND user_id = ?",
				Integer.class, postId, userId);
	}

}
