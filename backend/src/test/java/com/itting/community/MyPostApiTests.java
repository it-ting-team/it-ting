package com.itting.community;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

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
 * 내 활동: {@code GET /api/v1/me/posts}, {@code GET /api/v1/me/posts/summary}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MyPostApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CommunitySteps steps;

	long me;

	long author;

	Cookie session;

	Instant now;

	@BeforeEach
	void setUp() throws Exception {
		me = steps.createMember("me@example.com", "나두더지");
		author = steps.createMember("author@example.com", "두더지왕");
		session = steps.login(mockMvc, "me@example.com");
		now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	@Test
	void 내가_쓴_글은_새_글부터이고_가려진_글은_표시와_함께_나오며_지운_글은_없다() throws Exception {
		long old = steps.insertPost(me, "FREE", "예전 글", "본문", now.minusSeconds(300));
		long hidden = steps.insertPost(me, "PROMOTION", "HIDDEN", "가려진 글", "본문", now.minusSeconds(200));
		steps.insertPost(me, "FREE", "DELETED", "지운 글", "본문", now.minusSeconds(100));
		long latest = steps.insertPost(me, "REVIEW", "새 글", "본문", now);
		steps.insertPost(author, "FREE", "남의 글", "본문", now);

		mockMvc.perform(get("/api/v1/me/posts").param("tab", "WRITTEN").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) latest, (int) hidden, (int) old)))
				.andExpect(jsonPath("$.data.items[0].title").value("새 글"))
				.andExpect(jsonPath("$.data.items[0].author.id").value(me))
				.andExpect(jsonPath("$.data.items[0].isHidden").value(false))
				.andExpect(jsonPath("$.data.items[0].canEdit").value(true))
				.andExpect(jsonPath("$.data.items[0].bookmarkedAt").isEmpty())
				.andExpect(jsonPath("$.data.items[0].myLatestComment").isEmpty())
				.andExpect(jsonPath("$.data.items[0].myCommentCount").value(0))
				.andExpect(jsonPath("$.data.items[1].isHidden").value(true))
				.andExpect(jsonPath("$.data.items[1].canEdit").value(false))
				.andExpect(jsonPath("$.data.page").value(1))
				.andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.totalItems").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(1));

		mockMvc.perform(get("/api/v1/me/posts").param("tab", "WRITTEN").param("category", "PROMOTION")
						.cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) hidden)))
				.andExpect(jsonPath("$.data.totalItems").value(1));
	}

	@Test
	void 댓글_단_글은_글마다_한_줄이고_내가_최근에_댓글_단_글부터이며_최근_내_댓글과_수를_준다() throws Exception {
		long first = steps.insertPost(author, "FREE", "첫 글", "본문", now.minusSeconds(600));
		long second = steps.insertPost(author, "PROMOTION", "둘째 글", "본문", now.minusSeconds(500));
		steps.comment(first, me, null, null, "PUBLIC", "첫 글의 예전 댓글", now.minusSeconds(400));
		long parent = steps.comment(second, author, null, null, "PUBLIC", "작성자 댓글", now.minusSeconds(350));
		steps.comment(second, me, parent, null, "PUBLIC", "둘째 글의 답글", now.minusSeconds(300));
		long latestOnFirst = steps.comment(first, me, null, null, "PUBLIC", "첫 글의 최근 댓글", now.minusSeconds(200));
		steps.comment(first, me, null, null, "DELETED", "지운 댓글", now.minusSeconds(100));
		steps.addCommentVersion(latestOnFirst, 2, "첫 글의 최근 댓글(고침)");

		mockMvc.perform(get("/api/v1/me/posts").param("tab", "COMMENTED").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) first, (int) second)))
				.andExpect(jsonPath("$.data.items[0].myLatestComment.id").value(latestOnFirst))
				.andExpect(jsonPath("$.data.items[0].myLatestComment.content").value("첫 글의 최근 댓글(고침)"))
				.andExpect(jsonPath("$.data.items[0].myCommentCount").value(2))
				.andExpect(jsonPath("$.data.items[0].commentCount").value(2))
				.andExpect(jsonPath("$.data.items[0].canEdit").value(false))
				.andExpect(jsonPath("$.data.items[0].bookmarkedAt").isEmpty())
				.andExpect(jsonPath("$.data.items[1].myLatestComment.content").value("둘째 글의 답글"))
				.andExpect(jsonPath("$.data.items[1].myCommentCount").value(1))
				.andExpect(jsonPath("$.data.totalItems").value(2));
	}

	@Test
	void 댓글_단_글에서_가려지거나_지운_글과_내_댓글을_모두_지운_글은_빠진다() throws Exception {
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", now);
		long deleted = steps.insertPost(author, "FREE", "DELETED", "지운 글", "본문", now);
		long allDeleted = steps.insertPost(author, "FREE", "내 댓글을 다 지운 글", "본문", now);
		long kept = steps.insertPost(author, "FREE", "남은 글", "본문", now);
		steps.comment(hidden, me, null, "PUBLIC");
		steps.comment(deleted, me, null, "DELETED");
		steps.comment(allDeleted, me, null, "DELETED");
		steps.comment(kept, me, null, "HIDDEN");

		mockMvc.perform(get("/api/v1/me/posts").param("tab", "COMMENTED").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) kept)))
				.andExpect(jsonPath("$.data.items[0].myCommentCount").value(1));
	}

	@Test
	void 저장한_글은_최근에_저장한_글부터이고_가려진_글은_빠졌다가_복구되면_다시_나온다() throws Exception {
		long first = steps.insertPost(author, "FREE", "먼저 저장한 글", "본문", now.minusSeconds(100));
		long second = steps.insertPost(author, "FREE", "나중에 저장한 글", "본문", now.minusSeconds(200));
		long hidden = steps.insertPost(author, "FREE", "가려질 글", "본문", now);
		bookmark(first, now.minusSeconds(50));
		bookmark(second, now.minusSeconds(10));
		bookmark(hidden, now);
		jdbcTemplate.update("UPDATE post SET status = 'HIDDEN' WHERE id = ?", hidden);

		mockMvc.perform(get("/api/v1/me/posts").param("tab", "BOOKMARKED").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) second, (int) first)))
				.andExpect(jsonPath("$.data.items[0].bookmarkedAt").value(now.minusSeconds(10).toString()))
				.andExpect(jsonPath("$.data.items[0].isBookmarked").value(true))
				.andExpect(jsonPath("$.data.items[0].myLatestComment").isEmpty())
				.andExpect(jsonPath("$.data.items[0].myCommentCount").value(0))
				.andExpect(jsonPath("$.data.totalItems").value(2));

		jdbcTemplate.update("UPDATE post SET status = 'PUBLIC' WHERE id = ?", hidden);
		mockMvc.perform(get("/api/v1/me/posts").param("tab", "BOOKMARKED").cookie(session))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) hidden, (int) second, (int) first)));
	}

	@Test
	void 페이지를_나눠_준다() throws Exception {
		for (int i = 0; i < 3; i++) {
			steps.insertPost(me, "FREE", "글 " + i, "본문", now.minusSeconds(i));
		}

		mockMvc.perform(get("/api/v1/me/posts").param("tab", "WRITTEN").param("page", "2").param("size", "2")
						.cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(1)))
				.andExpect(jsonPath("$.data.items[0].title").value("글 2"))
				.andExpect(jsonPath("$.data.page").value(2))
				.andExpect(jsonPath("$.data.size").value(2))
				.andExpect(jsonPath("$.data.totalItems").value(3))
				.andExpect(jsonPath("$.data.totalPages").value(2));
	}

	@Test
	void 개수는_카테고리와_상관없이_세_탭의_글_수다() throws Exception {
		steps.insertPost(me, "FREE", "내 글", "본문", now);
		steps.insertPost(me, "PROMOTION", "HIDDEN", "가려진 내 글", "본문", now);
		steps.insertPost(me, "FREE", "DELETED", "지운 내 글", "본문", now);
		long other = steps.insertPost(author, "REVIEW", "남의 글", "본문", now);
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 남의 글", "본문", now);
		steps.comment(other, me, null, "PUBLIC");
		steps.comment(other, me, null, "PUBLIC");
		steps.comment(hidden, me, null, "PUBLIC");
		bookmark(other, now);
		bookmark(hidden, now);

		mockMvc.perform(get("/api/v1/me/posts/summary").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.writtenCount").value(2))
				.andExpect(jsonPath("$.data.commentedCount").value(1))
				.andExpect(jsonPath("$.data.bookmarkedCount").value(1));
	}

	@Test
	void 탭이_없거나_틀렸거나_size가_범위를_넘으면_400이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/posts").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("tab"));
		mockMvc.perform(get("/api/v1/me/posts").param("tab", "LIKED").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("tab"));
		mockMvc.perform(get("/api/v1/me/posts").param("tab", "WRITTEN").param("size", "51").cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("size"));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/me/posts").param("tab", "WRITTEN"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/v1/me/posts/summary"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private void bookmark(long postId, Instant at) {
		jdbcTemplate.update("INSERT INTO post_bookmark (post_id, user_id, created_at) VALUES (?, ?, ?)", postId, me,
				LocalDateTime.ofInstant(at, ZoneOffset.UTC));
	}

}
