package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.itting.support.CommunitySteps;
import com.itting.support.TestUsers;
import com.itting.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글을 바꾸는 API: 쓰기(댓글 · 답글), 수정, 삭제.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CommentWriteApiTests {

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

	/** 공개 글. 작성자는 author다. */
	long postId;

	Cookie session;

	Cookie authorSession;

	@BeforeEach
	void setUp() throws Exception {
		me = steps.createMember("me@example.com", "나두더지");
		author = steps.createMember("author@example.com", "두더지왕");
		third = steps.createMember("third@example.com", "새벽산책");
		postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		session = steps.login(mockMvc, "me@example.com");
		authorSession = steps.login(mockMvc, "author@example.com");
	}

	// ---- 쓰기 ----

	@Test
	void 댓글을_쓰면_201과_댓글의_모양과_쓴_뒤의_댓글_수를_주고_댓글과_버전_1을_저장한다() throws Exception {
		steps.comment(postId, third, null, "PUBLIC");
		steps.comment(postId, third, null, "HIDDEN");
		steps.comment(postId, third, null, "DELETED");

		String response = create(postId, "몇 시에 끝나나요?", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").isEmpty())
				.andExpect(jsonPath("$.data.author.id").value(me))
				.andExpect(jsonPath("$.data.author.nickname").value("나두더지"))
				.andExpect(jsonPath("$.data.author.isWithdrawn").value(false))
				.andExpect(jsonPath("$.data.content").value("몇 시에 끝나나요?"))
				.andExpect(jsonPath("$.data.mentionUser").isEmpty())
				.andExpect(jsonPath("$.data.createdAt").exists())
				.andExpect(jsonPath("$.data.isEdited").value(false))
				.andExpect(jsonPath("$.data.likeCount").value(0))
				.andExpect(jsonPath("$.data.isLiked").value(false))
				.andExpect(jsonPath("$.data.isMine").value(true))
				.andExpect(jsonPath("$.data.isHidden").value(false))
				.andExpect(jsonPath("$.data.isReportedByMe").value(false))
				.andExpect(jsonPath("$.data.parentCommentId").isEmpty())
				// 공개 1 + 가려진 1 + 새 댓글 1. 삭제된 댓글은 세지 않는다.
				.andExpect(jsonPath("$.data.commentCount").value(3))
				.andReturn().getResponse().getContentAsString();
		long commentId = ((Number) JsonPath.read(response, "$.data.id")).longValue();
		steps.flush();

		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT post_id, author_id, parent_id, mention_user_id, status, deleted_at FROM comment WHERE id = ?",
				commentId);
		assertThat(((Number) row.get("post_id")).longValue()).isEqualTo(postId);
		assertThat(((Number) row.get("author_id")).longValue()).isEqualTo(me);
		assertThat(row).containsEntry("parent_id", null).containsEntry("mention_user_id", null)
				.containsEntry("status", "PUBLIC").containsEntry("deleted_at", null);
		assertThat(jdbcTemplate.queryForMap("SELECT version, content FROM comment_version WHERE comment_id = ?",
				commentId)).containsEntry("version", 1).containsEntry("content", "몇 시에 끝나나요?");
	}

	@Test
	void 댓글에_답글을_달면_부모는_그_댓글이고_멘션은_없다() throws Exception {
		long top = steps.comment(postId, third, null, "PUBLIC");

		String response = create(postId, "9시쯤 끝나요!", top)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.parentCommentId").value(top))
				.andExpect(jsonPath("$.data.mentionUser").isEmpty())
				.andExpect(jsonPath("$.data.commentCount").value(2))
				.andReturn().getResponse().getContentAsString();
		long replyId = ((Number) JsonPath.read(response, "$.data.id")).longValue();
		steps.flush();

		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT parent_id, mention_user_id FROM comment WHERE id = ?", replyId);
		assertThat(((Number) row.get("parent_id")).longValue()).isEqualTo(top);
		assertThat(row.get("mention_user_id")).isNull();
	}

	@Test
	void 답글에_다시_답하면_같은_댓글_아래에_달고_멘션은_그_답글의_작성자다() throws Exception {
		long top = steps.comment(postId, third, null, "PUBLIC");
		long reply = steps.comment(postId, author, top, null, "PUBLIC", "9시쯤 끝나요!", null);

		String response = create(postId, "감사합니다. 신청할게요.", reply)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.parentCommentId").value(top))
				.andExpect(jsonPath("$.data.mentionUser.id").value(author))
				.andExpect(jsonPath("$.data.mentionUser.nickname").value("두더지왕"))
				.andExpect(jsonPath("$.data.commentCount").value(3))
				.andReturn().getResponse().getContentAsString();
		long replyId = ((Number) JsonPath.read(response, "$.data.id")).longValue();
		steps.flush();

		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT parent_id, mention_user_id FROM comment WHERE id = ?", replyId);
		// 답글의 답글도 맨 위 댓글 아래에 달린다. 답글이 부모가 되지는 않는다.
		assertThat(((Number) row.get("parent_id")).longValue()).isEqualTo(top);
		assertThat(((Number) row.get("mention_user_id")).longValue()).isEqualTo(author);
	}

	@Test
	void 본문은_코드_포인트로_세어_300자까지_쓸_수_있다() throws Exception {
		create(postId, "😀".repeat(300), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.content").value("😀".repeat(300)));
		create(postId, "가".repeat(300), null).andExpect(status().isCreated());

		create(postId, "😀".repeat(301), null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("content")));
		create(postId, "가".repeat(301), null).andExpect(status().isBadRequest());
	}

	@Test
	void 본문이_비었거나_공백만이거나_없으면_400이다() throws Exception {
		create(postId, "", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("content")));
		create(postId, "   \n ", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("content")));
		mockMvc.perform(post("/api/v1/posts/{id}/comments", postId).cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("content")));
		mockMvc.perform(post("/api/v1/posts/{id}/comments", postId).cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"본문\", \"replyToCommentId\": \"가\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("replyToCommentId")));

		assertThat(commentCount(postId)).isZero();
	}

	@Test
	void 가려진_글_삭제된_글_없는_글에는_댓글을_쓸_수_없다() throws Exception {
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());
		long deleted = steps.insertPost(author, "FREE", "DELETED", "지운 글", "본문", Instant.now());
		long myHidden = steps.insertPost(me, "FREE", "HIDDEN", "가려진 내 글", "본문", Instant.now());

		create(hidden, "댓글", null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_HIDDEN"));
		// 작성자가 써도 같다.
		create(myHidden, "댓글", null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_HIDDEN"));
		create(deleted, "댓글", null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
		create(999_999, "댓글", null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
		// 입력의 모양을 먼저 본다.
		create(hidden, " ", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment WHERE author_id = ?", Integer.class,
				me)).isZero();
	}

	@Test
	void 답할_댓글이_이_글에_없거나_삭제됐거나_가려졌으면_404_COMMENT_NOT_FOUND다() throws Exception {
		long otherPost = steps.insertPost(author, "FREE", "다른 글", "본문", Instant.now());
		long onOtherPost = steps.comment(otherPost, third, null, "PUBLIC");
		long hiddenTop = steps.comment(postId, third, null, "HIDDEN");
		long deletedTop = steps.comment(postId, third, null, "DELETED");
		long top = steps.comment(postId, third, null, "PUBLIC");
		long deletedReply = steps.comment(postId, third, top, "DELETED");
		long hiddenReply = steps.comment(postId, third, top, "HIDDEN");

		for (long target : List.of(onOtherPost, hiddenTop, deletedTop, deletedReply, hiddenReply, 999_999L)) {
			create(postId, "답글", target)
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
		}

		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment WHERE author_id = ?", Integer.class,
				me)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment_version cv JOIN comment c "
				+ "ON c.id = cv.comment_id WHERE c.author_id = ?", Integer.class, me)).isZero();
	}

	@Test
	void 댓글을_쓰려면_로그인과_온보딩이_필요하다() throws Exception {
		mockMvc.perform(post("/api/v1/posts/{id}/comments", postId).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"본문\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

		testUsers.createEmailUser("fresh@example.com", CommunitySteps.PASSWORD);
		mockMvc.perform(post("/api/v1/posts/{id}/comments", postId).cookie(steps.login(mockMvc, "fresh@example.com"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"본문\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ONBOARDING_INCOMPLETE"));
	}

	// ---- 수정 ----

	@Test
	void 본문이_바뀌면_새_버전을_더하고_isEdited가_true다() throws Exception {
		long comment = steps.comment(postId, me, null, null, "PUBLIC", "처음 본문", null);

		update(comment, "고친 본문")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.content").value("고친 본문"))
				.andExpect(jsonPath("$.data.isEdited").value(true))
				.andExpect(jsonPath("$.error").isEmpty());
		steps.flush();

		assertThat(jdbcTemplate.queryForList(
				"SELECT content FROM comment_version WHERE comment_id = ? ORDER BY version", String.class, comment))
				.containsExactly("처음 본문", "고친 본문");
		assertThat(jdbcTemplate.queryForList(
				"SELECT version FROM comment_version WHERE comment_id = ? ORDER BY version", Integer.class, comment))
				.containsExactly(1, 2);

		// 한 번 고친 댓글은 같은 본문으로 다시 저장해도 계속 "수정됨"이고 버전은 늘지 않는다.
		update(comment, "고친 본문")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isEdited").value(true));
		update(comment, "또 고친 본문")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").value("또 고친 본문"));
		steps.flush();
		assertThat(versions(comment)).isEqualTo(3);
	}

	@Test
	void 본문이_같으면_버전이_늘지_않고_isEdited는_false다() throws Exception {
		long comment = steps.comment(postId, me, null, null, "PUBLIC", "처음 본문", null);

		update(comment, "처음 본문")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").value("처음 본문"))
				.andExpect(jsonPath("$.data.isEdited").value(false));
		steps.flush();

		assertThat(versions(comment)).isEqualTo(1);
	}

	@Test
	void 답글도_쓴_사람이_고칠_수_있다() throws Exception {
		long top = steps.comment(postId, third, null, "PUBLIC");
		long reply = steps.comment(postId, me, top, author, "PUBLIC", "처음 답글", null);

		update(reply, "고친 답글")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isEdited").value(true));
	}

	@Test
	void 남의_댓글은_403_FORBIDDEN으로_고칠_수_없다() throws Exception {
		long others = steps.comment(postId, third, null, null, "PUBLIC", "남의 본문", null);

		update(others, "고친 본문")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		// 글 작성자여도 남의 댓글은 고칠 수 없다.
		mockMvc.perform(patch("/api/v1/comments/{id}", others).cookie(authorSession)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"고친 본문\"}"))
				.andExpect(status().isForbidden());
		assertThat(versions(others)).isEqualTo(1);
	}

	@Test
	void 없거나_삭제된_댓글은_404_COMMENT_NOT_FOUND다() throws Exception {
		long deleted = steps.comment(postId, me, null, "DELETED");

		update(deleted, "고친 본문")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
		update(999_999, "고친 본문")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
	}

	@Test
	void 가려진_댓글과_가려진_글의_댓글은_409_COMMENT_EDIT_NOT_ALLOWED로_고칠_수_없다() throws Exception {
		long hiddenComment = steps.comment(postId, me, null, "HIDDEN");
		long hiddenPost = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());
		long commentOnHiddenPost = steps.comment(hiddenPost, me, null, "PUBLIC");
		long othersOnHiddenPost = steps.comment(hiddenPost, third, null, "PUBLIC");

		update(hiddenComment, "고친 본문")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("COMMENT_EDIT_NOT_ALLOWED"));
		update(commentOnHiddenPost, "고친 본문")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("COMMENT_EDIT_NOT_ALLOWED"));
		// 쓴 사람인지를 먼저 본다.
		update(othersOnHiddenPost, "고친 본문")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

		assertThat(versions(hiddenComment)).isEqualTo(1);
		assertThat(versions(commentOnHiddenPost)).isEqualTo(1);
	}

	@Test
	void 수정의_본문이_어긋나면_400이다() throws Exception {
		long comment = steps.comment(postId, me, null, null, "PUBLIC", "처음 본문", null);

		update(comment, "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("content")));
		update(comment, "  ").andExpect(status().isBadRequest());
		update(comment, "가".repeat(301)).andExpect(status().isBadRequest());
		mockMvc.perform(patch("/api/v1/comments/{id}", comment).cookie(session)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());

		assertThat(versions(comment)).isEqualTo(1);
	}

	@Test
	void 댓글을_고치려면_로그인이_필요하다() throws Exception {
		mockMvc.perform(patch("/api/v1/comments/1").contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"본문\"}"))
				.andExpect(status().isUnauthorized());
	}

	// ---- 삭제 ----

	@Test
	void 댓글을_지우면_달린_답글도_지우고_남은_댓글_수를_준다() throws Exception {
		long top = steps.comment(postId, me, null, "PUBLIC");
		long publicReply = steps.comment(postId, third, top, "PUBLIC");
		long hiddenReply = steps.comment(postId, third, top, "HIDDEN");
		long deletedReply = steps.comment(postId, third, top, "DELETED");
		long otherTop = steps.comment(postId, third, null, "PUBLIC");
		long otherReply = steps.comment(postId, me, otherTop, "PUBLIC");
		LocalDateTime deletedAtBefore = jdbcTemplate.queryForObject("SELECT deleted_at FROM comment WHERE id = ?",
				LocalDateTime.class, deletedReply);

		remove(top)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				// 다른 댓글과 그 답글만 남는다.
				.andExpect(jsonPath("$.data.commentCount").value(2));
		steps.flush();

		for (long id : List.of(top, publicReply, hiddenReply)) {
			Map<String, Object> row = jdbcTemplate.queryForMap("SELECT status, deleted_at FROM comment WHERE id = ?",
					id);
			assertThat(row).containsEntry("status", "DELETED");
			assertThat(row.get("deleted_at")).isNotNull();
		}
		// 이미 지운 답글의 지운 시각은 바뀌지 않는다.
		assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM comment WHERE id = ?", LocalDateTime.class,
				deletedReply)).isEqualTo(deletedAtBefore);
		assertThat(statusOf(otherTop)).isEqualTo("PUBLIC");
		assertThat(statusOf(otherReply)).isEqualTo("PUBLIC");
		// 이전 내용(버전)은 2주 뒤까지 남는다.
		assertThat(versions(top)).isEqualTo(1);
		assertThat(versions(publicReply)).isEqualTo(1);
	}

	@Test
	void 답글만_지우면_다른_답글과_부모는_그대로다() throws Exception {
		long top = steps.comment(postId, third, null, "PUBLIC");
		long mine = steps.comment(postId, me, top, "PUBLIC");
		long sibling = steps.comment(postId, third, top, "PUBLIC");

		remove(mine)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.commentCount").value(2));
		steps.flush();

		assertThat(statusOf(mine)).isEqualTo("DELETED");
		assertThat(statusOf(top)).isEqualTo("PUBLIC");
		assertThat(statusOf(sibling)).isEqualTo("PUBLIC");
	}

	@Test
	void 가려진_댓글과_가려진_글의_내_댓글도_지울_수_있다() throws Exception {
		long hiddenComment = steps.comment(postId, me, null, "HIDDEN");
		long hiddenPost = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());
		long commentOnHiddenPost = steps.comment(hiddenPost, me, null, "PUBLIC");
		long replyOnHiddenPost = steps.comment(hiddenPost, third, commentOnHiddenPost, "PUBLIC");

		remove(hiddenComment)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.commentCount").value(0));
		remove(commentOnHiddenPost)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.commentCount").value(0));
		steps.flush();

		assertThat(statusOf(hiddenComment)).isEqualTo("DELETED");
		assertThat(statusOf(commentOnHiddenPost)).isEqualTo("DELETED");
		assertThat(statusOf(replyOnHiddenPost)).isEqualTo("DELETED");
	}

	@Test
	void 남의_댓글은_글_작성자도_403_FORBIDDEN으로_지울_수_없다() throws Exception {
		long mine = steps.comment(postId, me, null, "PUBLIC");
		long others = steps.comment(postId, third, null, "PUBLIC");

		remove(others)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		mockMvc.perform(delete("/api/v1/comments/{id}", mine).cookie(authorSession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		steps.flush();

		assertThat(statusOf(others)).isEqualTo("PUBLIC");
		assertThat(statusOf(mine)).isEqualTo("PUBLIC");
	}

	@Test
	void 이미_지웠거나_없는_댓글은_404_COMMENT_NOT_FOUND다() throws Exception {
		long comment = steps.comment(postId, me, null, "PUBLIC");

		remove(comment).andExpect(status().isOk());
		remove(comment)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
		remove(999_999)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
	}

	@Test
	void 댓글을_지우려면_로그인이_필요하다() throws Exception {
		mockMvc.perform(delete("/api/v1/comments/1")).andExpect(status().isUnauthorized());
	}

	// ---- 도우미 ----

	private ResultActions create(long targetPostId, String content, Long replyToCommentId) throws Exception {
		String body = replyToCommentId == null
				? "{\"content\": \"%s\"}".formatted(escape(content))
				: "{\"content\": \"%s\", \"replyToCommentId\": %d}".formatted(escape(content), replyToCommentId);
		return mockMvc.perform(post("/api/v1/posts/{id}/comments", targetPostId).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions update(long commentId, String content) throws Exception {
		return mockMvc.perform(patch("/api/v1/comments/{id}", commentId).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\": \"%s\"}".formatted(escape(content))));
	}

	private ResultActions remove(long commentId) throws Exception {
		return mockMvc.perform(delete("/api/v1/comments/{id}", commentId).cookie(session));
	}

	private static String escape(String content) {
		return content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private String statusOf(long commentId) {
		return jdbcTemplate.queryForObject("SELECT status FROM comment WHERE id = ?", String.class, commentId);
	}

	private int versions(long commentId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment_version WHERE comment_id = ?",
				Integer.class, commentId);
	}

	private int commentCount(long targetPostId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment WHERE post_id = ?", Integer.class,
				targetPostId);
	}

}
