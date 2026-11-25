package com.itting.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import com.itting.support.CommunitySteps;
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
 * 글을 바꾸는 API: 쓰기, 수정, 삭제, 조회수 올리기.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PostWriteApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CommunitySteps steps;

	long me;

	long other;

	Cookie session;

	@BeforeEach
	void setUp() throws Exception {
		me = steps.createMember("me@example.com", "나두더지");
		other = steps.createMember("other@example.com", "두더지왕");
		session = steps.login(mockMvc, "me@example.com");
	}

	@Test
	void 글을_쓰면_201과_id를_주고_버전_태그_사진을_순서대로_저장한다() throws Exception {
		long first = steps.uploadImage(me, "https://cdn.example/1.webp");
		long second = steps.uploadImage(me, "https://cdn.example/2.webp");

		String response = create(body("FREE", "커피머신 쓰는 법", "물통을 먼저 채워요 ☕", "[3, 1]",
				"[%d, %d]".formatted(second, first), "null"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.postId").exists())
				.andReturn().getResponse().getContentAsString();
		long postId = ((Number) JsonPath.read(response, "$.data.postId")).longValue();
		steps.flush();

		Map<String, Object> post = jdbcTemplate.queryForMap(
				"SELECT author_id, category, status, view_count, deleted_at FROM post WHERE id = ?", postId);
		assertThat(((Number) post.get("author_id")).longValue()).isEqualTo(me);
		assertThat(post).containsEntry("category", "FREE").containsEntry("status", "PUBLIC")
				.containsEntry("deleted_at", null);
		assertThat(((Number) post.get("view_count")).intValue()).isZero();
		assertThat(jdbcTemplate.queryForMap("SELECT version, title, content FROM post_version WHERE post_id = ?",
				postId))
				.containsEntry("version", 1)
				.containsEntry("title", "커피머신 쓰는 법")
				.containsEntry("content", "물통을 먼저 채워요 ☕");
		assertThat(jdbcTemplate.queryForList(
				"SELECT interest_category_id FROM post_interest WHERE post_id = ? ORDER BY interest_category_id",
				Long.class, postId)).containsExactly(1L, 3L);
		assertThat(jdbcTemplate.queryForList("SELECT image_id FROM post_image WHERE post_id = ? ORDER BY sort_order",
				Long.class, postId)).containsExactly(second, first);
	}

	@Test
	void 글쓰기의_입력이_어긋나면_400과_어긋난_필드를_모두_준다() throws Exception {
		long othersImage = steps.uploadImage(other, "https://cdn.example/o.webp");

		create(body("FREE", "가".repeat(51), "   ", "[1, 2, 3, 4, 5, 6]", "[%d]".formatted(othersImage), "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.error.fields[*].field").value(
						contains("content", "imageIds", "interestCategoryIds", "title")));

		create(body("FREE", "제목", "본문", "[1, 1]", "[]", "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("interestCategoryIds")));
		create(body("FREE", "제목", "본문", "[999]", "[]", "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].message").value("없는 관심사가 있습니다."));
		create(body("FREE", "제목", "본문", "[]", "[999999]", "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("imageIds")));
		create(body("NOTICE", "제목", "본문", "[]", "[]", "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("category")));
	}

	@Test
	void 다른_글에_붙은_사진은_붙일_수_없다() throws Exception {
		long image = steps.uploadImage(me, "https://cdn.example/1.webp");
		long existing = steps.insertPost(me, "FREE", "먼저 쓴 글", "본문", Instant.now());
		steps.attachImage(existing, 1, image);

		create(body("FREE", "제목", "본문", "[]", "[%d]".formatted(image), "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0].message").value("붙일 수 없는 사진이 있습니다."));
	}

	@Test
	void 모임_홍보와_후기에_모임이_없으면_400_없는_모임을_보내면_404_MEETUP_NOT_FOUND다() throws Exception {
		create(body("PROMOTION", "제목", "본문", "[]", "[]", "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("meetupId")));
		create(body("REVIEW", "제목", "본문", "[]", "[]", "999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_FOUND"));
		create(body("FREE", "제목", "본문", "[]", "[]", "999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_FOUND"));
	}

	@Test
	void 제목을_바꾸면_버전이_늘고_태그와_사진만_바꾸면_버전이_그대로다() throws Exception {
		long kept = steps.uploadImage(me, "https://cdn.example/1.webp");
		long removed = steps.uploadImage(me, "https://cdn.example/2.webp");
		long added = steps.uploadImage(me, "https://cdn.example/3.webp");
		long postId = createdId(create(body("FREE", "제목", "본문", "[1, 2]",
				"[%d, %d]".formatted(kept, removed), "null")));

		update(postId, body("FREE", "제목", "본문", "[2, 4]", "[%d, %d]".formatted(added, kept), "null"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").doesNotExist());
		steps.flush();
		assertThat(versions(postId)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForList(
				"SELECT interest_category_id FROM post_interest WHERE post_id = ? ORDER BY interest_category_id",
				Long.class, postId)).containsExactly(2L, 4L);
		assertThat(jdbcTemplate.queryForList("SELECT image_id FROM post_image WHERE post_id = ? ORDER BY sort_order",
				Long.class, postId)).containsExactly(added, kept);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM uploaded_image WHERE id = ?", Integer.class,
				removed)).isEqualTo(1);

		update(postId, body("REVIEW", "새 제목", "본문", "[]", "[]", "null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("meetupId")));
		update(postId, body("FREE", "새 제목", "본문", "[]", "[]", "null"))
				.andExpect(status().isOk());
		steps.flush();
		assertThat(versions(postId)).isEqualTo(2);
		mockMvc.perform(get("/api/v1/posts/{id}", postId).cookie(session))
				.andExpect(jsonPath("$.data.title").value("새 제목"))
				.andExpect(jsonPath("$.data.isEdited").value(true))
				.andExpect(jsonPath("$.data.images").isEmpty());
	}

	@Test
	void 수정에서_meetupId_키가_빠지면_400이다() throws Exception {
		long postId = steps.insertPost(me, "FREE", "제목", "본문", Instant.now());

		update(postId, """
				{ "category": "FREE", "title": "제목", "content": "본문", "interestCategoryIds": [], "imageIds": [] }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("meetupId")));
		update(postId, body("FREE", "제목", "본문", "[]", "[]", "999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("MEETUP_NOT_FOUND"));
		update(postId, body("FREE", "제목", "본문", "[]", "[]", "\"모임\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[*].field").value(contains("meetupId")));
	}

	@Test
	void 남의_글은_403_가려진_글은_409_삭제된_글은_404로_고칠_수_없다() throws Exception {
		long others = steps.insertPost(other, "FREE", "남의 글", "본문", Instant.now());
		long hidden = steps.insertPost(me, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());
		long deleted = steps.insertPost(me, "FREE", "DELETED", "지운 글", "본문", Instant.now());
		String valid = body("FREE", "제목", "본문", "[]", "[]", "null");

		update(others, valid)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		update(hidden, valid)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("POST_EDIT_NOT_ALLOWED"));
		update(deleted, valid)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
		// 권한을 먼저 보므로 남의 글에는 잘못된 본문을 보내도 403이다.
		update(others, body("FREE", "", "", "[]", "[]", "null"))
				.andExpect(status().isForbidden());
	}

	@Test
	void 글을_지우면_댓글과_답글도_지우고_좋아요와_저장은_남긴다() throws Exception {
		long postId = steps.insertPost(me, "FREE", "HIDDEN", "가려진 내 글", "본문", Instant.now());
		long comment = steps.comment(postId, other, null, "PUBLIC");
		steps.comment(postId, me, comment, "HIDDEN");
		steps.like(postId, other, Instant.now());
		steps.bookmark(postId, other);

		mockMvc.perform(delete("/api/v1/posts/{id}", postId).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
		steps.flush();

		Map<String, Object> post = jdbcTemplate.queryForMap("SELECT status, deleted_at FROM post WHERE id = ?",
				postId);
		assertThat(post.get("status")).isEqualTo("DELETED");
		assertThat(post.get("deleted_at")).isNotNull();
		assertThat(jdbcTemplate.queryForList("SELECT status FROM comment WHERE post_id = ?", String.class, postId))
				.containsOnly("DELETED");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM comment WHERE post_id = ? AND deleted_at IS NULL", Integer.class, postId))
				.isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ?", Integer.class,
				postId)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_bookmark WHERE post_id = ?", Integer.class,
				postId)).isEqualTo(1);

		mockMvc.perform(delete("/api/v1/posts/{id}", postId).cookie(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
	}

	@Test
	void 남의_글은_지울_수_없다() throws Exception {
		long others = steps.insertPost(other, "FREE", "남의 글", "본문", Instant.now());

		mockMvc.perform(delete("/api/v1/posts/{id}", others).cookie(session))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM post WHERE id = ?", String.class, others))
				.isEqualTo("PUBLIC");
	}

	@Test
	void 조회수는_부를_때마다_오르고_작성자가_불러도_오른다() throws Exception {
		long mine = steps.insertPost(me, "FREE", "내 글", "본문", Instant.now());

		views(mine).andExpect(status().isOk()).andExpect(jsonPath("$.data.viewCount").value(1));
		views(mine).andExpect(status().isOk()).andExpect(jsonPath("$.data.viewCount").value(2));
		mockMvc.perform(get("/api/v1/posts/{id}", mine).cookie(session))
				.andExpect(jsonPath("$.data.viewCount").value(2));
	}

	@Test
	void 삭제된_글의_조회수는_404_POST_NOT_FOUND_가려진_글은_작성자여도_404_POST_HIDDEN이다() throws Exception {
		long deleted = steps.insertPost(me, "FREE", "DELETED", "지운 글", "본문", Instant.now());
		long hidden = steps.insertPost(me, "FREE", "HIDDEN", "가려진 글", "본문", Instant.now());

		views(deleted).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
		views(hidden).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("POST_HIDDEN"));
		views(999_999).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
	}

	@Test
	void 로그인하지_않으면_401이다() throws Exception {
		mockMvc.perform(post("/api/v1/posts").contentType(MediaType.APPLICATION_JSON)
				.content(body("FREE", "제목", "본문", "[]", "[]", "null")))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/api/v1/posts/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/posts/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/posts/1/views")).andExpect(status().isUnauthorized());
	}

	private ResultActions create(String body) throws Exception {
		return mockMvc.perform(post("/api/v1/posts").cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions update(long postId, String body) throws Exception {
		return mockMvc.perform(patch("/api/v1/posts/{id}", postId).cookie(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions views(long postId) throws Exception {
		return mockMvc.perform(post("/api/v1/posts/{id}/views", postId).cookie(session));
	}

	private long createdId(ResultActions result) throws Exception {
		String response = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.postId")).longValue();
	}

	private int versions(long postId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_version WHERE post_id = ?", Integer.class,
				postId);
	}

	private static String body(String category, String title, String content, String interestCategoryIds,
			String imageIds, String meetupId) {
		return """
				{
				  "category": "%s",
				  "title": "%s",
				  "content": "%s",
				  "interestCategoryIds": %s,
				  "imageIds": %s,
				  "meetupId": %s
				}
				""".formatted(category, title, content, interestCategoryIds, imageIds, meetupId);
	}

}
