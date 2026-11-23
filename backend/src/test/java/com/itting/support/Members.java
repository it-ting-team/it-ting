package com.itting.support;

import static com.itting.support.TestUsers.sessionCookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 온보딩을 마친 회원을 API로 만들고, 방명록 글을 DB에 바로 넣는다. 방명록을 쓰는 API(05 탐색)가 생기기 전까지 글은 SQL로 넣는다.
 * MockMvc가 있는 테스트에서 {@code new Members(...)}로 만들어 쓴다.
 */
public class Members {

	public static final String PASSWORD = "mole1234!";

	private final MockMvc mockMvc;

	private final TestUsers testUsers;

	private final OnboardingSteps steps;

	private final JdbcTemplate jdbcTemplate;

	public Members(MockMvc mockMvc, TestUsers testUsers, OnboardingSteps steps, JdbcTemplate jdbcTemplate) {
		this.mockMvc = mockMvc;
		this.testUsers = testUsers;
		this.steps = steps;
		this.jdbcTemplate = jdbcTemplate;
	}

	/** 15기 · 서울 2층으로 가입하고, 관심사 · 질문을 id가 작은 것부터 3개씩 골라 온보딩을 마친 회원. */
	public Member onboard(String email, String nickname) throws Exception {
		return onboard(email, nickname, steps.interestIds(3), steps.questionIds(3));
	}

	public Member onboard(String email, String nickname, List<Long> interestIds, List<Long> questionIds)
			throws Exception {
		long userId = testUsers.createEmailUser(email, PASSWORD);
		Cookie session = login(email);
		String response = mockMvc.perform(post("/api/v1/me/onboarding/complete")
						.cookie(session)
						.contentType(MediaType.APPLICATION_JSON)
						.content(steps.completeBody(nickname, interestIds, questionIds)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long burrowId = ((Number) JsonPath.read(response, "$.data.burrowId")).longValue();
		return new Member(userId, burrowId, session);
	}

	public Cookie login(String email) throws Exception {
		return sessionCookie(mockMvc.perform(post("/api/v1/sessions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "email": "%s", "password": "%s" }
								""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated())
				.andReturn().getResponse());
	}

	/** 공개 상태의 방명록 글을 넣는다. 쓴 시각을 정해 순서를 맞춘다. */
	public long writeGuestbook(long burrowId, long authorId, String content, Instant createdAt) {
		return insertGuestbook(burrowId, authorId, content, "PUBLIC", createdAt);
	}

	/** 신고로 가려진 방명록 글을 넣는다. */
	public long writeHiddenGuestbook(long burrowId, long authorId, String content, Instant createdAt) {
		return insertGuestbook(burrowId, authorId, content, "HIDDEN", createdAt);
	}

	private long insertGuestbook(long burrowId, long authorId, String content, String status, Instant createdAt) {
		LocalDateTime at = LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC);
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			var statement = connection.prepareStatement("""
					INSERT INTO guestbook_entry (burrow_id, author_id, content, status, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?)
					""", new String[] { "id" });
			statement.setLong(1, burrowId);
			statement.setLong(2, authorId);
			statement.setString(3, content);
			statement.setString(4, status);
			statement.setObject(5, at);
			statement.setObject(6, at);
			return statement;
		}, keyHolder);
		return keyHolder.getKey().longValue();
	}

	/**
	 * 온보딩을 마친 회원.
	 *
	 * @param session 로그인한 세션 쿠키
	 */
	public record Member(long userId, long burrowId, Cookie session) {
	}

}
