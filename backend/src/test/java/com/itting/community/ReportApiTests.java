package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.itting.support.CommunitySteps;
import com.itting.support.TestcontainersConfiguration;
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
 * 신고: {@code POST /api/v1/reports}와, 신고한 사람에게 보이는 {@code isReportedByMe}.
 * 동시에 들어온 신고는 {@link ReportConcurrencyTests}가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ReportApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CommunitySteps steps;

	long me;

	long author;

	long second;

	long third;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		me = steps.createMember("me@example.com", "나두더지");
		author = steps.createMember("author@example.com", "두더지왕");
		second = steps.createMember("second@example.com", "새벽산책");
		third = steps.createMember("third@example.com", "점심러버");
		session = steps.login(mockMvc, "me@example.com");
	}

	@Test
	void 글을_신고하면_201과_신고_id를_주고_지금_버전을_적는다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		steps.addVersion(postId, 2, "고친 글", "고친 본문", Instant.now());

		long reportId = report("POST", postId, "SPAM", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.reportId").isNumber())
				.andExpect(jsonPath("$.error").isEmpty())
				.andReturn().getResponse().getContentAsString().transform(ReportApiTests::reportId);

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM report WHERE id = ?", reportId);
		assertThat(row.get("reporter_id")).isEqualTo(me);
		assertThat(row.get("target_type")).isEqualTo("POST");
		assertThat(row.get("target_id")).isEqualTo(postId);
		assertThat(row.get("target_version")).isEqualTo(2);
		assertThat(row.get("reason")).isEqualTo("SPAM");
		assertThat(row.get("detail")).isNull();
		assertThat(row.get("status")).isEqualTo("RECEIVED");
		assertThat(postStatus(postId)).isEqualTo("PUBLIC");
		assertThat(notifications(author)).isEmpty();
	}

	@Test
	void 댓글_사용자_방명록_글도_신고할_수_있고_기타_사유는_상세_내용을_적는다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		long commentId = steps.comment(postId, author, null, "PUBLIC");
		long burrowId = steps.insertBurrow(second);
		long entryId = steps.insertGuestbook(burrowId, author, "PUBLIC");

		report("COMMENT", commentId, "ABUSE", null).andExpect(status().isCreated());
		report("MEMBER", author, "ETC", "프로필 사진이 불쾌해요").andExpect(status().isCreated());
		report("GUESTBOOK", entryId, "OBSCENE", null).andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForList(
				"SELECT target_type, target_id, target_version, reason, detail FROM report WHERE reporter_id = ? ORDER BY id",
				me))
				.extracting(row -> row.get("target_type"), row -> row.get("target_id"), row -> row.get("target_version"),
						row -> row.get("reason"), row -> row.get("detail"))
				.containsExactly(
						tuple("COMMENT", commentId, 1, "ABUSE", null),
						tuple("MEMBER", author, null, "ETC", "프로필 사진이 불쾌해요"),
						tuple("GUESTBOOK", entryId, null, "OBSCENE", null));
	}

	@Test
	void 서로_다른_세_번째_신고에서_글이_가려지고_작성자에게_알림이_간다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		steps.insertReport(second, "POST", postId);
		assertThat(postStatus(postId)).isEqualTo("PUBLIC");
		steps.insertReport(third, "POST", postId);

		report("POST", postId, "SPAM", null).andExpect(status().isCreated());

		assertThat(postStatus(postId)).isEqualTo("HIDDEN");
		assertThat(notifications(author)).containsExactly(notification("POST_HIDDEN", "POST", postId));
		assertThat(notifications(me)).isEmpty();
	}

	@Test
	void 두_번째_신고까지는_가려지지_않는다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		steps.insertReport(second, "POST", postId);

		report("POST", postId, "SPAM", null).andExpect(status().isCreated());

		assertThat(postStatus(postId)).isEqualTo("PUBLIC");
		assertThat(notifications(author)).isEmpty();
	}

	@Test
	void 댓글은_세_번째_신고에서_가려지고_답글은_그대로이며_알림은_그_글로_간다() throws Exception {
		long postId = steps.insertPost(second, "FREE", "글", "본문", Instant.now());
		long commentId = steps.comment(postId, author, null, "PUBLIC");
		long replyId = steps.comment(postId, third, commentId, "PUBLIC");
		steps.insertReport(second, "COMMENT", commentId);
		steps.insertReport(third, "COMMENT", commentId);

		report("COMMENT", commentId, "ABUSE", null).andExpect(status().isCreated());

		assertThat(commentStatus(commentId)).isEqualTo("HIDDEN");
		assertThat(commentStatus(replyId)).isEqualTo("PUBLIC");
		assertThat(notifications(author)).containsExactly(notification("COMMENT_HIDDEN", "POST", postId));
	}

	@Test
	void 방명록_글은_세_번째_신고에서_가려지고_쓴_사람에게_땅굴로_가는_알림이_간다() throws Exception {
		long burrowId = steps.insertBurrow(second);
		long entryId = steps.insertGuestbook(burrowId, author, "PUBLIC");
		steps.insertReport(second, "GUESTBOOK", entryId);
		steps.insertReport(third, "GUESTBOOK", entryId);

		report("GUESTBOOK", entryId, "SPAM", null).andExpect(status().isCreated());

		steps.flush();
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM guestbook_entry WHERE id = ?", String.class,
				entryId)).isEqualTo("HIDDEN");
		assertThat(notifications(author)).containsExactly(notification("GUESTBOOK_ENTRY_HIDDEN", "BURROW", second));
	}

	@Test
	void 사용자는_신고가_쌓여도_자동으로_조치하지_않는다() throws Exception {
		steps.insertReport(second, "MEMBER", author);
		steps.insertReport(third, "MEMBER", author);

		report("MEMBER", author, "ABUSE", null).andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForObject("SELECT status FROM `user` WHERE id = ?", String.class, author))
				.isEqualTo("ACTIVE");
		assertThat(notifications(author)).isEmpty();
	}

	@Test
	void 복구한_뒤에는_그_뒤에_접수된_신고만_세어_다시_가린다() throws Exception {
		long fourth = steps.createMember("fourth@example.com", "넷째");
		long fifth = steps.createMember("fifth@example.com", "다섯째");
		long sixth = steps.createMember("sixth@example.com", "여섯째");
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		steps.insertReport(fourth, "POST", postId);
		steps.insertReport(fifth, "POST", postId);
		long last = steps.insertReport(sixth, "POST", postId);
		steps.insertRestore(sixth, "POST", postId, last);

		steps.insertReport(second, "POST", postId);
		Cookie thirdSession = steps.login(mockMvc, "third@example.com");
		report(thirdSession, "POST", postId, "SPAM", null).andExpect(status().isCreated());
		assertThat(postStatus(postId)).isEqualTo("PUBLIC");

		report("POST", postId, "SPAM", null).andExpect(status().isCreated());
		assertThat(postStatus(postId)).isEqualTo("HIDDEN");
		assertThat(notifications(author)).hasSize(1);
	}

	@Test
	void 신고한_사람에게는_목록_상세_댓글에서_isReportedByMe가_참이다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		long other = steps.insertPost(author, "FREE", "다른 글", "본문", Instant.now().minusSeconds(60));
		long commentId = steps.comment(postId, author, null, "PUBLIC");
		long otherComment = steps.comment(postId, second, null, "PUBLIC");
		report("POST", postId, "SPAM", null).andExpect(status().isCreated());
		report("COMMENT", commentId, "SPAM", null).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/posts").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(postId))
				.andExpect(jsonPath("$.data.items[0].isReportedByMe").value(true))
				.andExpect(jsonPath("$.data.items[0].title").value("글"))
				.andExpect(jsonPath("$.data.items[1].id").value(other))
				.andExpect(jsonPath("$.data.items[1].isReportedByMe").value(false));
		mockMvc.perform(get("/api/v1/posts/{id}", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isReportedByMe").value(true))
				.andExpect(jsonPath("$.data.content").value("본문"));
		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(commentId))
				.andExpect(jsonPath("$.data.items[0].isReportedByMe").value(true))
				.andExpect(jsonPath("$.data.items[0].content").value("댓글입니다"))
				.andExpect(jsonPath("$.data.items[1].id").value(otherComment))
				.andExpect(jsonPath("$.data.items[1].isReportedByMe").value(false));

		Cookie secondSession = steps.login(mockMvc, "second@example.com");
		mockMvc.perform(get("/api/v1/posts/{id}", postId).cookie(secondSession))
				.andExpect(jsonPath("$.data.isReportedByMe").value(false));
	}

	@Test
	void 입력이_어긋나면_400이다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());

		report("POST", postId, "ETC", null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[0].field").value("detail"));
		report("POST", postId, "ETC", "   ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("detail"));
		report("POST", postId, "ETC", "가".repeat(101))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("detail"));
		report("POST", postId, "SPAM", "기타가 아닌데 보냄")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("detail"));
		mockMvc.perform(post("/api/v1/reports").cookie(session).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "targetType": "PHOTO", "targetId": 1, "reason": "SPAM", "detail": null }
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("targetType"));
		mockMvc.perform(post("/api/v1/reports").cookie(session).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "targetType": "POST", "reason": "SPAM" }
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].field").value("targetId"));

		report("POST", postId, "ETC", "가".repeat(100)).andExpect(status().isCreated());
	}

	@Test
	void 내_것을_신고하면_403이다() throws Exception {
		long postId = steps.insertPost(me, "FREE", "내 글", "본문", Instant.now());
		long commentId = steps.comment(postId, me, null, "PUBLIC");
		long burrowId = steps.insertBurrow(second);
		long entryId = steps.insertGuestbook(burrowId, me, "PUBLIC");

		for (Object[] target : List.of(new Object[] { "POST", postId }, new Object[] { "COMMENT", commentId },
				new Object[] { "MEMBER", me }, new Object[] { "GUESTBOOK", entryId })) {
			report((String) target[0], (Long) target[1], "SPAM", null)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code").value("OWN_TARGET_NOT_ALLOWED"));
		}
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report", Integer.class)).isZero();
	}

	@Test
	void 없거나_지워졌거나_가려진_대상은_404다() throws Exception {
		long hiddenPost = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());
		long deletedPost = steps.insertPost(author, "FREE", "DELETED", "지운 글", "본문", Instant.now());
		long publicPost = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		long hiddenComment = steps.comment(publicPost, author, null, "HIDDEN");
		long deletedComment = steps.comment(publicPost, author, null, "DELETED");
		long commentOnHiddenPost = steps.comment(hiddenPost, second, null, "PUBLIC");
		long burrowId = steps.insertBurrow(second);
		long hiddenEntry = steps.insertGuestbook(burrowId, author, "HIDDEN");
		jdbcTemplate.update("UPDATE `user` SET status = 'WITHDRAWN' WHERE id = ?", third);

		for (Object[] target : List.of(new Object[] { "POST", 999_999L }, new Object[] { "POST", hiddenPost },
				new Object[] { "POST", deletedPost }, new Object[] { "COMMENT", 999_999L },
				new Object[] { "COMMENT", hiddenComment }, new Object[] { "COMMENT", deletedComment },
				new Object[] { "COMMENT", commentOnHiddenPost }, new Object[] { "MEMBER", 999_999L },
				new Object[] { "MEMBER", third }, new Object[] { "GUESTBOOK", 999_999L },
				new Object[] { "GUESTBOOK", hiddenEntry })) {
			report((String) target[0], (Long) target[1], "SPAM", null)
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value("REPORT_TARGET_NOT_FOUND"));
		}
	}

	@Test
	void 같은_대상을_다시_신고하면_409다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		report("POST", postId, "SPAM", null).andExpect(status().isCreated());

		report("POST", postId, "ABUSE", null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ALREADY_REPORTED"))
				.andExpect(jsonPath("$.error.message").value("이미 신고한 대상입니다."));
	}

	@Test
	void 복구된_뒤에도_예전에_신고한_사람은_다시_신고할_수_없다() throws Exception {
		long postId = steps.insertPost(author, "FREE", "글", "본문", Instant.now());
		long reportId = steps.insertReport(me, "POST", postId);
		steps.insertRestore(second, "POST", postId, reportId);

		report("POST", postId, "SPAM", null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("ALREADY_REPORTED"));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "targetType": "MEMBER", "targetId": %d, "reason": "SPAM", "detail": null }
								""".formatted(author)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	private ResultActions report(String targetType, long targetId, String reason, String detail) throws Exception {
		return report(session, targetType, targetId, reason, detail);
	}

	private ResultActions report(Cookie cookie, String targetType, long targetId, String reason, String detail)
			throws Exception {
		String detailJson = detail == null ? "null" : "\"" + detail + "\"";
		return mockMvc.perform(post("/api/v1/reports").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "targetType": "%s", "targetId": %d, "reason": "%s", "detail": %s }
						""".formatted(targetType, targetId, reason, detailJson)));
	}

	private String postStatus(long postId) {
		steps.flush();
		return jdbcTemplate.queryForObject("SELECT status FROM post WHERE id = ?", String.class, postId);
	}

	private String commentStatus(long commentId) {
		steps.flush();
		return jdbcTemplate.queryForObject("SELECT status FROM comment WHERE id = ?", String.class, commentId);
	}

	private List<Map<String, Object>> notifications(long userId) {
		return jdbcTemplate.queryForList(
				"SELECT type, target_type, target_id, actor_id FROM notification WHERE user_id = ? ORDER BY id", userId);
	}

	private static Map<String, Object> notification(String type, String targetType, long targetId) {
		Map<String, Object> row = new HashMap<>();
		row.put("type", type);
		row.put("target_type", targetType);
		row.put("target_id", targetId);
		row.put("actor_id", null);
		return row;
	}

	private static long reportId(String body) {
		return Long.parseLong(body.replaceAll(".*\"reportId\":(\\d+).*", "$1"));
	}

}
