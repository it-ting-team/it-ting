package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글의 댓글 목록: {@code GET /api/v1/posts/{postId}/comments}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CommentReadApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CommunitySteps steps;

	@Autowired
	TestUsers testUsers;

	Instant base;

	long me;

	long author;

	long third;

	/** 공개 글. 작성자는 author다. */
	long postId;

	Cookie session;

	Cookie authorSession;

	@BeforeEach
	void setUp() throws Exception {
		base = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
		me = steps.createMember("me@example.com", "나두더지");
		author = steps.createMember("author@example.com", "두더지왕");
		third = steps.createMember("third@example.com", "새벽산책");
		postId = steps.insertPost(author, "FREE", "글", "본문", base);
		session = steps.login(mockMvc, "me@example.com");
		authorSession = steps.login(mockMvc, "author@example.com");
	}

	@Test
	void 댓글을_등록순으로_주고_답글을_붙이고_삭제는_빼고_가려진_댓글은_본문이_null이다() throws Exception {
		long first = steps.comment(postId, third, null, null, "PUBLIC", "몇 시에 끝나나요?", at(1));
		long second = steps.comment(postId, me, null, null, "PUBLIC", "저도 가고 싶어요", at(2));
		steps.addCommentVersion(second, 2, "저도 가고 싶어요!");
		long hidden = steps.comment(postId, author, null, null, "HIDDEN", "가려질 본문", at(3));
		steps.comment(postId, third, null, null, "DELETED", "지운 댓글", at(4));
		// 가려진 댓글의 답글은 그대로 나온다. 지운 답글은 빠진다. 답글은 등록순이다.
		long laterReply = steps.comment(postId, author, first, null, "PUBLIC", "9시쯤 끝나요!", at(6));
		long mentionReply = steps.comment(postId, third, first, author, "PUBLIC", "감사합니다.", at(7));
		steps.comment(postId, me, first, null, "DELETED", "지운 답글", at(8));
		long earlierReply = steps.comment(postId, me, first, null, "PUBLIC", "저도 궁금해요", at(5));
		long replyOfHidden = steps.comment(postId, me, hidden, null, "PUBLIC", "이 댓글의 답글", at(9));
		steps.commentLike(first, me);
		steps.commentLike(first, third);
		steps.commentLike(laterReply, me);

		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.error").isEmpty())
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").isEmpty())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) first, (int) second, (int) hidden)))
				// 첫 댓글: 남이 쓴 댓글, 좋아요 2개(내 것 포함), 답글 3개(지운 것 빼고 등록순)
				.andExpect(jsonPath("$.data.items[0].content").value("몇 시에 끝나나요?"))
				.andExpect(jsonPath("$.data.items[0].author.id").value(third))
				.andExpect(jsonPath("$.data.items[0].author.nickname").value("새벽산책"))
				.andExpect(jsonPath("$.data.items[0].author.isWithdrawn").value(false))
				.andExpect(jsonPath("$.data.items[0].mentionUser").isEmpty())
				.andExpect(jsonPath("$.data.items[0].createdAt").value(inSeconds(at(1))))
				.andExpect(jsonPath("$.data.items[0].isEdited").value(false))
				.andExpect(jsonPath("$.data.items[0].likeCount").value(2))
				.andExpect(jsonPath("$.data.items[0].isLiked").value(true))
				.andExpect(jsonPath("$.data.items[0].isMine").value(false))
				.andExpect(jsonPath("$.data.items[0].isHidden").value(false))
				.andExpect(jsonPath("$.data.items[0].isReportedByMe").value(false))
				.andExpect(jsonPath("$.data.items[0].replies[*].id")
						.value(contains((int) earlierReply, (int) laterReply, (int) mentionReply)))
				.andExpect(jsonPath("$.data.items[0].replies[0].isMine").value(true))
				.andExpect(jsonPath("$.data.items[0].replies[1].likeCount").value(1))
				.andExpect(jsonPath("$.data.items[0].replies[1].isLiked").value(true))
				.andExpect(jsonPath("$.data.items[0].replies[1].mentionUser").isEmpty())
				.andExpect(jsonPath("$.data.items[0].replies[2].mentionUser.id").value(author))
				.andExpect(jsonPath("$.data.items[0].replies[2].mentionUser.nickname").value("두더지왕"))
				.andExpect(jsonPath("$.data.items[0].replies[2].isLiked").value(false))
				// 둘째 댓글: 내 댓글, 한 번 고쳤다. 답글이 없으면 []
				.andExpect(jsonPath("$.data.items[1].content").value("저도 가고 싶어요!"))
				.andExpect(jsonPath("$.data.items[1].isMine").value(true))
				.andExpect(jsonPath("$.data.items[1].isEdited").value(true))
				.andExpect(jsonPath("$.data.items[1].likeCount").value(0))
				.andExpect(jsonPath("$.data.items[1].replies").isEmpty())
				// 가려진 댓글: 자리를 지키고 본문은 null이다. 답글은 그대로 나온다.
				.andExpect(jsonPath("$.data.items[2].id").value(hidden))
				.andExpect(jsonPath("$.data.items[2].isHidden").value(true))
				.andExpect(jsonPath("$.data.items[2].content").isEmpty())
				.andExpect(jsonPath("$.data.items[2].author.id").value(author))
				.andExpect(jsonPath("$.data.items[2].replies[*].id").value(contains((int) replyOfHidden)))
				.andExpect(jsonPath("$.data.items[2].replies[0].content").value("이 댓글의 답글"));
	}

	@Test
	void 가려진_댓글의_본문은_작성자에게도_주지_않는다() throws Exception {
		steps.comment(postId, me, null, null, "HIDDEN", "가려질 내 본문", at(1));

		String response = mockMvc.perform(get("/api/v1/posts/{id}/comments", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].isHidden").value(true))
				.andExpect(jsonPath("$.data.items[0].isMine").value(true))
				.andExpect(jsonPath("$.data.items[0].content").isEmpty())
				.andReturn().getResponse().getContentAsString();
		assertThat(response).doesNotContain("가려질 내 본문");
	}

	@Test
	void size개씩_끊고_커서로_이어_받는다_답글은_size에_세지_않는다() throws Exception {
		List<Long> ids = new ArrayList<>();
		for (int i = 1; i <= 5; i++) {
			ids.add(steps.comment(postId, third, null, null, "PUBLIC", "댓글 " + i, at(i)));
		}
		steps.comment(postId, me, ids.get(0), null, "PUBLIC", "답글 1", at(10));
		steps.comment(postId, me, ids.get(0), null, "PUBLIC", "답글 2", at(11));
		steps.comment(postId, me, ids.get(0), null, "PUBLIC", "답글 3", at(12));

		String firstPage = mockMvc.perform(get("/api/v1/posts/{id}/comments?size=2", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(2)))
				.andExpect(jsonPath("$.data.items[*].id").value(contains(ids.get(0).intValue(), ids.get(1).intValue())))
				.andExpect(jsonPath("$.data.items[0].replies", hasSize(3)))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		String firstCursor = JsonPath.read(firstPage, "$.data.nextCursor");
		String secondPage = mockMvc.perform(get("/api/v1/posts/{id}/comments", postId)
						.param("size", "2").param("cursor", firstCursor)
						.cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains(ids.get(2).intValue(), ids.get(3).intValue())))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andReturn().getResponse().getContentAsString();
		String secondCursor = JsonPath.read(secondPage, "$.data.nextCursor");
		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId)
						.param("size", "2").param("cursor", secondCursor)
						.cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains(ids.get(4).intValue())))
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").isEmpty());
	}

	@Test
	void 쓴_시각이_같은_댓글도_id_순으로_빠지거나_겹치지_않고_이어_받는다() throws Exception {
		long a = steps.comment(postId, third, null, null, "PUBLIC", "a", at(1));
		long b = steps.comment(postId, third, null, null, "PUBLIC", "b", at(1));
		long c = steps.comment(postId, third, null, null, "PUBLIC", "c", at(1));

		List<Long> received = new ArrayList<>();
		String cursor = null;
		boolean hasNext = true;
		while (hasNext) {
			var request = get("/api/v1/posts/{id}/comments", postId).param("size", "1").cookie(session);
			if (cursor != null) {
				request.param("cursor", cursor);
			}
			String page = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
					.getContentAsString();
			received.add(((Number) JsonPath.read(page, "$.data.items[0].id")).longValue());
			cursor = JsonPath.read(page, "$.data.nextCursor");
			hasNext = JsonPath.read(page, "$.data.hasNext");
		}

		assertThat(received).containsExactly(a, b, c);
	}

	@Test
	void 이어_받는_사이에_지워진_댓글은_건너뛰고_새_댓글은_뒤에_붙는다() throws Exception {
		long first = steps.comment(postId, third, null, null, "PUBLIC", "첫째", at(1));
		long second = steps.comment(postId, third, null, null, "PUBLIC", "둘째", at(2));
		long thirdComment = steps.comment(postId, third, null, null, "PUBLIC", "셋째", at(3));

		String page = mockMvc.perform(get("/api/v1/posts/{id}/comments?size=1", postId).cookie(session))
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) first)))
				.andReturn().getResponse().getContentAsString();
		long latest = steps.comment(postId, third, null, null, "PUBLIC", "새 댓글", at(30));
		jdbcTemplate.update("UPDATE comment SET status = 'DELETED', deleted_at = UTC_TIMESTAMP(6) WHERE id = ?",
				second);

		String cursor = JsonPath.read(page, "$.data.nextCursor");
		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId).param("cursor", cursor).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[*].id").value(contains((int) thirdComment, (int) latest)))
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	void size를_보내지_않으면_10개씩_준다() throws Exception {
		for (int i = 1; i <= 12; i++) {
			steps.comment(postId, third, null, null, "PUBLIC", "댓글 " + i, at(i));
		}

		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(10)))
				.andExpect(jsonPath("$.data.hasNext").value(true));
		mockMvc.perform(get("/api/v1/posts/{id}/comments?size=100", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items", hasSize(12)))
				.andExpect(jsonPath("$.data.hasNext").value(false));
	}

	@Test
	void 댓글이_없으면_빈_목록이다() throws Exception {
		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").isEmpty());
	}

	@Test
	void 탈퇴한_사람의_댓글과_멘션은_닉네임이_없다() throws Exception {
		long top = steps.comment(postId, third, null, null, "PUBLIC", "댓글", at(1));
		steps.comment(postId, me, top, third, "PUBLIC", "답글", at(2));
		long withdrawnComment = steps.comment(postId, third, null, null, "PUBLIC", "탈퇴한 사람의 댓글", at(3));
		testUsers.withdraw(third);

		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].author.id").value(third))
				.andExpect(jsonPath("$.data.items[0].author.isWithdrawn").value(true))
				.andExpect(jsonPath("$.data.items[0].author.nickname").isEmpty())
				.andExpect(jsonPath("$.data.items[0].replies[0].mentionUser.id").value(third))
				.andExpect(jsonPath("$.data.items[0].replies[0].mentionUser.nickname").isEmpty())
				.andExpect(jsonPath("$.data.items[1].id").value(withdrawnComment))
				.andExpect(jsonPath("$.data.items[1].content").value("탈퇴한 사람의 댓글"));
	}

	@Test
	void 가려진_글은_작성자만_댓글을_볼_수_있다() throws Exception {
		long hidden = steps.insertPost(author, "FREE", "HIDDEN", "가려진 글", "본문", base);
		steps.comment(hidden, third, null, null, "PUBLIC", "가려진 글의 댓글", at(1));

		mockMvc.perform(get("/api/v1/posts/{id}/comments", hidden).cookie(authorSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].content").value("가려진 글의 댓글"));
		mockMvc.perform(get("/api/v1/posts/{id}/comments", hidden).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("POST_HIDDEN"));
	}

	@Test
	void 삭제된_글과_없는_글은_404_POST_NOT_FOUND다() throws Exception {
		long deleted = steps.insertPost(author, "FREE", "DELETED", "지운 글", "본문", base);

		mockMvc.perform(get("/api/v1/posts/{id}/comments", deleted).cookie(authorSession))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/posts/{id}/comments", 999_999).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
	}

	@Test
	void size가_범위를_벗어나거나_커서가_깨졌으면_400_INVALID_INPUT이다() throws Exception {
		mockMvc.perform(get("/api/v1/posts/{id}/comments?size=101", postId).cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("size")));
		mockMvc.perform(get("/api/v1/posts/{id}/comments?size=0", postId).cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(get("/api/v1/posts/{id}/comments?size=abc", postId).cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("size")));
		mockMvc.perform(get("/api/v1/posts/{id}/comments?cursor=깨진-커서", postId).cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("cursor")));
		mockMvc.perform(get("/api/v1/posts/{id}/comments?cursor=Zm9v", postId).cookie(session))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("cursor")));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/posts/{id}/comments", postId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	/** 응답의 시각은 초 단위 ISO 8601이다. */
	private static String inSeconds(Instant instant) {
		return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
	}

	/** 기준 시각에서 {@code minutes}분 뒤. */
	private Instant at(int minutes) {
		return base.plus(Duration.ofMinutes(minutes));
	}

}
