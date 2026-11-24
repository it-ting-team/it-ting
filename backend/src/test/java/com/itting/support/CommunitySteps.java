package com.itting.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 커뮤니티 테스트가 함께 쓰는 값. 글 · 댓글 · 좋아요 · 저장 · 사진을 DB에 바로 넣는다(테스트의 준비 단계라 API를 거치지 않는다).
 * 시각은 UTC로 넣는다.
 */
@Component
public class CommunitySteps {

	public static final String PASSWORD = "mole1234!";

	private final JdbcTemplate jdbcTemplate;

	private final TestUsers testUsers;

	@PersistenceContext
	private EntityManager entityManager;

	public CommunitySteps(JdbcTemplate jdbcTemplate, TestUsers testUsers) {
		this.jdbcTemplate = jdbcTemplate;
		this.testUsers = testUsers;
	}

	/** 온보딩을 마친 계정. 닉네임을 붙이고, 땅굴이 없으므로 새 학기 고르기에도 막히지 않는다. */
	public long createMember(String email, String nickname) {
		long userId = testUsers.createEmailUser(email, PASSWORD);
		jdbcTemplate.update("UPDATE user_profile SET nickname = ?, mole_head = 'CAP_RED' WHERE user_id = ?",
				nickname, userId);
		testUsers.markOnboarded(userId);
		return userId;
	}

	public Cookie login(MockMvc mockMvc, String email) throws Exception {
		return TestUsers.sessionCookie(mockMvc.perform(post("/api/v1/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "%s", "password": "%s" }
								""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

	/** 공개 글. 제목과 본문은 버전 1이다. */
	public long insertPost(long authorId, String category, String title, String content, Instant createdAt) {
		return insertPost(authorId, category, "PUBLIC", title, content, createdAt);
	}

	public long insertPost(long authorId, String category, String status, String title, String content,
			Instant createdAt) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO post (author_id, category, status, view_count, created_at, updated_at, deleted_at)
					VALUES (?, ?, ?, 0, ?, ?, ?)
					""", new String[] { "id" });
			statement.setLong(1, authorId);
			statement.setString(2, category);
			statement.setString(3, status);
			statement.setObject(4, utc(createdAt));
			statement.setObject(5, utc(createdAt));
			statement.setObject(6, "DELETED".equals(status) ? utc(createdAt) : null);
			return statement;
		}, keyHolder);
		long postId = keyHolder.getKey().longValue();
		addVersion(postId, 1, title, content, createdAt);
		return postId;
	}

	public void addVersion(long postId, int version, String title, String content, Instant createdAt) {
		jdbcTemplate.update("""
				INSERT INTO post_version (post_id, version, title, content, created_at) VALUES (?, ?, ?, ?, ?)
				""", postId, version, title, content, utc(createdAt));
	}

	public void tag(long postId, long... interestCategoryIds) {
		for (long id : interestCategoryIds) {
			jdbcTemplate.update("INSERT INTO post_interest (post_id, interest_category_id) VALUES (?, ?)", postId, id);
		}
	}

	public void like(long postId, long userId, Instant at) {
		jdbcTemplate.update("INSERT INTO post_like (post_id, user_id, created_at) VALUES (?, ?, ?)", postId, userId,
				utc(at));
	}

	public void bookmark(long postId, long userId) {
		jdbcTemplate.update("INSERT INTO post_bookmark (post_id, user_id, created_at) VALUES (?, ?, UTC_TIMESTAMP(6))",
				postId, userId);
	}

	/** 댓글(답글이면 parentId). 본문은 버전 1이다. */
	public long comment(long postId, long authorId, Long parentId, String status) {
		return comment(postId, authorId, parentId, null, status, "댓글입니다", null);
	}

	/**
	 * 댓글(답글이면 parentId, 답글에 다시 답했으면 mentionUserId). 본문은 버전 1이다.
	 *
	 * @param createdAt 쓴 시각. null이면 지금
	 */
	public long comment(long postId, long authorId, Long parentId, Long mentionUserId, String status, String content,
			Instant createdAt) {
		LocalDateTime at = createdAt == null ? LocalDateTime.now(ZoneOffset.UTC) : utc(createdAt);
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO comment (post_id, author_id, parent_id, mention_user_id, status, created_at, updated_at,
					                     deleted_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""", new String[] { "id" });
			statement.setLong(1, postId);
			statement.setLong(2, authorId);
			statement.setObject(3, parentId);
			statement.setObject(4, mentionUserId);
			statement.setString(5, status);
			statement.setObject(6, at);
			statement.setObject(7, at);
			statement.setObject(8, "DELETED".equals(status) ? at : null);
			return statement;
		}, keyHolder);
		long commentId = keyHolder.getKey().longValue();
		addCommentVersion(commentId, 1, content, at);
		return commentId;
	}

	public void addCommentVersion(long commentId, int version, String content) {
		addCommentVersion(commentId, version, content, LocalDateTime.now(ZoneOffset.UTC));
	}

	private void addCommentVersion(long commentId, int version, String content, LocalDateTime at) {
		jdbcTemplate.update("""
				INSERT INTO comment_version (comment_id, version, content, created_at) VALUES (?, ?, ?, ?)
				""", commentId, version, content, at);
	}

	public void commentLike(long commentId, long userId) {
		jdbcTemplate.update(
				"INSERT INTO comment_like (comment_id, user_id, created_at) VALUES (?, ?, UTC_TIMESTAMP(6))", commentId,
				userId);
	}

	/** 올린 사진. 올리는 API가 생기기 전이라 줄을 바로 넣는다. */
	public long uploadImage(long uploaderId, String url) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO uploaded_image (uploader_id, image_url, created_at) VALUES (?, ?, UTC_TIMESTAMP(6))
					""", new String[] { "id" });
			statement.setLong(1, uploaderId);
			statement.setString(2, url);
			return statement;
		}, keyHolder);
		return keyHolder.getKey().longValue();
	}

	public void attachImage(long postId, int sortOrder, long imageId) {
		jdbcTemplate.update("""
				INSERT INTO post_image (post_id, sort_order, image_id, created_at) VALUES (?, ?, ?, UTC_TIMESTAMP(6))
				""", postId, sortOrder, imageId);
	}

	/** 이 계정이 고른 세부 관심사를 바꾼다. 고른 순서대로 sort_order를 1부터 준다. */
	public void setInterests(long userId, List<Long> interestIds) {
		jdbcTemplate.update("DELETE FROM user_interest WHERE user_id = ?", userId);
		for (int i = 0; i < interestIds.size(); i++) {
			jdbcTemplate.update("""
					INSERT INTO user_interest (user_id, interest_id, sort_order, created_at, updated_at)
					VALUES (?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
					""", userId, interestIds.get(i), i + 1);
		}
	}

	/** 이 대분류에 속한 세부 관심사 가운데 id가 가장 작은 것. */
	public long firstInterestOf(long interestCategoryId) {
		return jdbcTemplate.queryForObject("SELECT MIN(id) FROM interest WHERE interest_category_id = ?", Long.class,
				interestCategoryId);
	}

	/** API가 JPA로 미뤄 둔 변경을 DB에 쓰고 비운다. SQL로 확인하기 전에 부른다. */
	public void flush() {
		entityManager.flush();
		entityManager.clear();
	}

	public static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
