package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.itting.community.service.PostTargetReader;
import com.itting.notification.domain.NotificationTargetType;
import com.itting.notification.service.NotificationTargetSummary;
import com.itting.support.CommunitySteps;
import com.itting.support.NotificationSteps;
import com.itting.support.TestUsers;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 · 댓글 · 답글이 만드는 알림과, 커뮤니티 알림에 싣는 글 제목({@link PostTargetReader}).
 * 자기 자신 · 탈퇴한 사람 · 알림을 끈 사람을 거르는 것은 {@code NotificationService}가 하고, 여기서는 그 결과만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CommunityNotificationTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CommunitySteps steps;

	@Autowired
	NotificationSteps notificationSteps;

	@Autowired
	TestUsers testUsers;

	@Autowired
	PostTargetReader postTargetReader;

	Instant now;

	long me;

	long author;

	long third;

	/** 공개 글. 작성자는 author다. */
	long postId;

	Cookie session;

	Cookie authorSession;

	Cookie thirdSession;

	@BeforeEach
	void setUp() throws Exception {
		now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		me = steps.createMember("me@example.com", "나두더지");
		author = steps.createMember("author@example.com", "두더지왕");
		third = steps.createMember("third@example.com", "새벽산책");
		postId = steps.insertPost(author, "FREE", "토요일 아침 러닝", "본문", now);
		session = steps.login(mockMvc, "me@example.com");
		authorSession = steps.login(mockMvc, "author@example.com");
		thirdSession = steps.login(mockMvc, "third@example.com");
	}

	// ---- 글 좋아요 ----

	@Test
	void 글에_좋아요를_누르면_글_작성자에게_알림을_한_번_만들고_취소하고_다시_눌러도_늘지_않는다() throws Exception {
		mockMvc.perform(put("/api/v1/posts/{id}/like", postId).cookie(session)).andExpect(status().isOk());

		List<Map<String, Object>> created = notifications(author);
		assertThat(created).hasSize(1);
		assertThat(created.getFirst())
				.containsEntry("type", "POST_LIKED")
				.containsEntry("target_type", "POST")
				.containsEntry("event_key", "POST_LIKED:" + postId + ":" + me);
		assertThat(((Number) created.getFirst().get("target_id")).longValue()).isEqualTo(postId);
		assertThat(((Number) created.getFirst().get("actor_id")).longValue()).isEqualTo(me);

		// 이미 누른 글, 취소, 다시 누르기: 알림은 그대로 한 줄이다. 취소해도 이미 만든 알림은 지우지 않는다.
		mockMvc.perform(put("/api/v1/posts/{id}/like", postId).cookie(session)).andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/posts/{id}/like", postId).cookie(session)).andExpect(status().isOk());
		assertThat(notifications(author)).hasSize(1);
		mockMvc.perform(put("/api/v1/posts/{id}/like", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(1));
		assertThat(notifications(author)).hasSize(1);

		// 다른 사람이 누르면 새 알림이다.
		mockMvc.perform(put("/api/v1/posts/{id}/like", postId).cookie(thirdSession)).andExpect(status().isOk());
		assertThat(notifications(author)).hasSize(2);
		assertThat(notifications(me)).isEmpty();
		assertThat(notifications(third)).isEmpty();
	}

	@Test
	void 내_글에_좋아요를_눌러도_알림은_없다() throws Exception {
		mockMvc.perform(put("/api/v1/posts/{id}/like", postId).cookie(authorSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.likeCount").value(1));

		assertThat(notifications(author)).isEmpty();
	}

	@Test
	void 좋아요가_실패하면_알림도_없다() throws Exception {
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", now);

		mockMvc.perform(put("/api/v1/posts/{id}/like", hidden).cookie(session))
				.andExpect(status().isNotFound());

		assertThat(notifications(author)).isEmpty();
	}

	// ---- 댓글 좋아요 ----

	@Test
	void 댓글에_좋아요를_누르면_댓글_작성자에게_글로_가는_알림을_한_번_만든다() throws Exception {
		long comment = steps.comment(postId, third, null, "PUBLIC");

		mockMvc.perform(put("/api/v1/comments/{id}/like", comment).cookie(session)).andExpect(status().isOk());

		List<Map<String, Object>> created = notifications(third);
		assertThat(created).hasSize(1);
		assertThat(created.getFirst())
				.containsEntry("type", "COMMENT_LIKED")
				.containsEntry("target_type", "POST")
				.containsEntry("event_key", "COMMENT_LIKED:" + comment + ":" + me);
		// 갈 곳은 댓글이 달린 글이다.
		assertThat(((Number) created.getFirst().get("target_id")).longValue()).isEqualTo(postId);
		assertThat(((Number) created.getFirst().get("actor_id")).longValue()).isEqualTo(me);

		mockMvc.perform(put("/api/v1/comments/{id}/like", comment).cookie(session)).andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/comments/{id}/like", comment).cookie(session)).andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/comments/{id}/like", comment).cookie(session)).andExpect(status().isOk());
		assertThat(notifications(third)).hasSize(1);
		// 글 작성자에게는 가지 않는다.
		assertThat(notifications(author)).isEmpty();
	}

	@Test
	void 내_댓글에_좋아요를_누르거나_가려진_댓글에_누르면_알림은_없다() throws Exception {
		long mine = steps.comment(postId, me, null, "PUBLIC");
		long hidden = steps.comment(postId, third, null, "HIDDEN");

		mockMvc.perform(put("/api/v1/comments/{id}/like", mine).cookie(session)).andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/comments/{id}/like", hidden).cookie(session)).andExpect(status().isNotFound());

		assertThat(notifications(me)).isEmpty();
		assertThat(notifications(third)).isEmpty();
	}

	// ---- 댓글 · 답글 ----

	@Test
	void 댓글을_쓰면_글_작성자에게_글로_가는_알림을_만든다() throws Exception {
		comment(session, postId, "몇 시에 끝나나요?", null);
		comment(thirdSession, postId, "저도 궁금해요", null);

		List<Map<String, Object>> created = notifications(author);
		assertThat(created).hasSize(2);
		assertThat(created).allSatisfy(row -> {
			assertThat(row).containsEntry("type", "POST_COMMENTED").containsEntry("target_type", "POST")
					.containsEntry("event_key", null);
			assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(postId);
		});
		assertThat(((Number) created.get(0).get("actor_id")).longValue()).isEqualTo(me);
		assertThat(((Number) created.get(1).get("actor_id")).longValue()).isEqualTo(third);
	}

	@Test
	void 내_글에_내가_댓글을_쓰면_알림은_없다() throws Exception {
		comment(authorSession, postId, "공지드려요", null);

		assertThat(notifications(author)).isEmpty();
	}

	@Test
	void 답글을_쓰면_답한_대상의_작성자에게만_알림을_만든다() throws Exception {
		long top = steps.comment(postId, third, null, "PUBLIC");

		comment(session, postId, "답글이에요", top);

		List<Map<String, Object>> created = notifications(third);
		assertThat(created).hasSize(1);
		assertThat(created.getFirst()).containsEntry("type", "COMMENT_REPLIED").containsEntry("target_type", "POST");
		assertThat(((Number) created.getFirst().get("target_id")).longValue()).isEqualTo(postId);
		assertThat(((Number) created.getFirst().get("actor_id")).longValue()).isEqualTo(me);
		// 글 작성자에게는 댓글 알림이 가지 않는다.
		assertThat(notifications(author)).isEmpty();
	}

	@Test
	void 답글에_다시_답하면_그_답글의_작성자에게_알림을_만든다() throws Exception {
		long top = steps.comment(postId, third, null, "PUBLIC");
		long reply = steps.comment(postId, author, top, null, "PUBLIC", "답글", null);

		comment(session, postId, "답글에 답해요", reply);

		List<Map<String, Object>> created = notifications(author);
		assertThat(created).hasSize(1);
		assertThat(created.getFirst()).containsEntry("type", "COMMENT_REPLIED");
		// 맨 위 댓글의 작성자에게는 가지 않는다.
		assertThat(notifications(third)).isEmpty();
	}

	@Test
	void 내_댓글에_내가_답하면_알림은_없다() throws Exception {
		long mine = steps.comment(postId, me, null, "PUBLIC");

		comment(session, postId, "덧붙여요", mine);

		assertThat(notifications(me)).isEmpty();
		assertThat(notifications(author)).isEmpty();
	}

	@Test
	void 댓글이_실패하면_알림도_없다() throws Exception {
		long hiddenTop = steps.comment(postId, third, null, "HIDDEN");

		mockMvc.perform(post("/api/v1/posts/{id}/comments", postId).cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"답글\", \"replyToCommentId\": %d}".formatted(hiddenTop)))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/posts/{id}/comments", postId).cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\": \"  \"}"))
				.andExpect(status().isBadRequest());

		assertThat(notifications(author)).isEmpty();
		assertThat(notifications(third)).isEmpty();
	}

	// ---- 거르는 경우 ----

	@Test
	void 커뮤니티_알림을_끈_사람에게는_알림을_만들지_않지만_일은_성공한다() throws Exception {
		notificationSteps.setting(author, true, true, false, true, true);

		mockMvc.perform(put("/api/v1/posts/{id}/like", postId).cookie(session)).andExpect(status().isOk());
		comment(session, postId, "댓글", null);

		assertThat(notifications(author)).isEmpty();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ?", Integer.class,
				postId)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comment WHERE post_id = ?", Integer.class,
				postId)).isEqualTo(1);
	}

	@Test
	void 탈퇴한_사람에게는_알림을_만들지_않지만_일은_성공한다() throws Exception {
		testUsers.withdraw(author);

		mockMvc.perform(put("/api/v1/posts/{id}/like", postId).cookie(session)).andExpect(status().isOk());
		comment(session, postId, "댓글", null);

		assertThat(notifications(author)).isEmpty();
	}

	// ---- 알림 목록의 글 제목 ----

	@Test
	void 만든_알림이_알림_목록에_글_제목과_함께_나온다() throws Exception {
		mockMvc.perform(put("/api/v1/posts/{id}/like", postId).cookie(session)).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/me/notifications").cookie(authorSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].type").value("POST_LIKED"))
				.andExpect(jsonPath("$.data.items[0].category").value("COMMUNITY"))
				.andExpect(jsonPath("$.data.items[0].isRead").value(false))
				.andExpect(jsonPath("$.data.items[0].actor.id").value(me))
				.andExpect(jsonPath("$.data.items[0].target.type").value("POST"))
				.andExpect(jsonPath("$.data.items[0].target.id").value(postId))
				.andExpect(jsonPath("$.data.items[0].target.title").value("토요일 아침 러닝"))
				.andExpect(jsonPath("$.data.items[0].target.startsAt").isEmpty());
	}

	@Test
	void 커뮤니티_알림에_글의_최신_제목을_싣고_가려진_글도_제목을_싣고_삭제된_글은_null이다() throws Exception {
		long edited = steps.insertPost(author, "FREE", "처음 제목", "본문", now);
		steps.addVersion(edited, 2, "고친 제목", "고친 본문", now.plusSeconds(1));
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글 제목", "본문", now);
		long deleted = steps.insertPost(author, "FREE", "DELETED", "지운 글 제목", "본문", now);
		notificationSteps.insertPostCommented(author, me, edited, now.minus(Duration.ofMinutes(1)), null);
		notificationSteps.insertPostCommented(author, me, hidden, now.minus(Duration.ofMinutes(2)), null);
		notificationSteps.insertPostCommented(author, me, deleted, now.minus(Duration.ofMinutes(3)), null);

		mockMvc.perform(get("/api/v1/me/notifications").cookie(authorSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].target.id").value(edited))
				.andExpect(jsonPath("$.data.items[0].target.title").value("고친 제목"))
				.andExpect(jsonPath("$.data.items[1].target.id").value(hidden))
				.andExpect(jsonPath("$.data.items[1].target.title").value("가려진 글 제목"))
				.andExpect(jsonPath("$.data.items[2].target.id").value(deleted))
				.andExpect(jsonPath("$.data.items[2].target.title").isEmpty());
		// 팝업도 같은 읽기 구현을 쓴다.
		mockMvc.perform(get("/api/v1/me/notifications/popup").cookie(authorSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recentItems[0].target.title").value("고친 제목"))
				.andExpect(jsonPath("$.data.recentItems[2].target.title").isEmpty());
	}

	@Test
	void 글_제목_읽기는_삭제한_글과_없는_글을_빼고_가려진_글은_제목을_준다() {
		long edited = steps.insertPost(author, "FREE", "처음 제목", "본문", now);
		steps.addVersion(edited, 2, "고친 제목", "고친 본문", now.plusSeconds(1));
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글 제목", "본문", now);
		long deleted = steps.insertPost(author, "FREE", "DELETED", "지운 글 제목", "본문", now);

		assertThat(postTargetReader.targetType()).isEqualTo(NotificationTargetType.POST);
		Map<Long, NotificationTargetSummary> read = postTargetReader.read(List.of(edited, hidden, deleted, 999_999L));

		assertThat(read).containsOnlyKeys(edited, hidden);
		assertThat(read.get(edited)).isEqualTo(new NotificationTargetSummary("고친 제목", null));
		assertThat(read.get(hidden)).isEqualTo(new NotificationTargetSummary("가려진 글 제목", null));
		assertThat(postTargetReader.read(List.of())).isEmpty();
	}

	// ---- 도우미 ----

	private void comment(Cookie who, long targetPostId, String content, Long replyToCommentId) throws Exception {
		String body = replyToCommentId == null
				? "{\"content\": \"%s\"}".formatted(content)
				: "{\"content\": \"%s\", \"replyToCommentId\": %d}".formatted(content, replyToCommentId);
		mockMvc.perform(post("/api/v1/posts/{id}/comments", targetPostId).cookie(who)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated());
	}

	/** 이 사람이 받은 알림을 만든 순서로. */
	private List<Map<String, Object>> notifications(long userId) {
		return jdbcTemplate.queryForList("""
				SELECT type, target_type, target_id, actor_id, event_key FROM notification WHERE user_id = ?
				ORDER BY id
				""", userId);
	}

}
